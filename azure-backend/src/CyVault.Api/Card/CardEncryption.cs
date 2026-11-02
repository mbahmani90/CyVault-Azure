using System.Security.Cryptography;
using System.Text;
using Azure.Core;
using Azure.Security.KeyVault.Keys.Cryptography;

namespace CyVault.Api.Card;

/// <summary>A card number encrypted with its own data key, and that data key wrapped by card-key.</summary>
public sealed record EncryptedCardNumber(string Ciphertext, string WrappedKey, string KeyId);

/// <summary>
/// Envelope encryption of card numbers: each card number is
/// encrypted with its own random AES-256 data key, and that key is wrapped with card-key, an RSA key
/// in Key Vault. Wrapping uses the public key; unwrapping needs the private key, which never leaves
/// the vault.
/// </summary>
public sealed class CardEncryption(Uri keyId, TokenCredential credential)
{
    private const int DataKeySize = 32; // AES-256
    private const int NonceSize = 12; // AES-GCM standard
    private const int TagSize = 16;

    // Without a version, so the SDK wraps with card-key's current version.
    private readonly CryptographyClient _currentKey = new(keyId, credential);

    public async Task<EncryptedCardNumber> EncryptAsync(string cardNumber, CancellationToken cancellationToken)
    {
        var dataKey = RandomNumberGenerator.GetBytes(DataKeySize);
        try
        {
            var plaintext = Encoding.UTF8.GetBytes(cardNumber);
            var nonce = RandomNumberGenerator.GetBytes(NonceSize);
            var ciphertext = new byte[plaintext.Length];
            var tag = new byte[TagSize];
            using (var aes = new AesGcm(dataKey, TagSize))
            {
                aes.Encrypt(nonce, plaintext, ciphertext, tag);
            }

            var wrapped = await _currentKey.WrapKeyAsync(KeyWrapAlgorithm.RsaOaep256, dataKey, cancellationToken);

            // nonce | ciphertext | tag, so one column holds everything AES-GCM needs to decrypt.
            return new EncryptedCardNumber(
                Convert.ToBase64String([.. nonce, .. ciphertext, .. tag]),
                Convert.ToBase64String(wrapped.EncryptedKey),
                // The versioned ID, so this card still unwraps after card-key is rotated.
                wrapped.KeyId);
        }
        finally
        {
            CryptographicOperations.ZeroMemory(dataKey);
        }
    }

    public async Task<string> DecryptAsync(EncryptedCardNumber encrypted, CancellationToken cancellationToken)
    {
        // The key version that wrapped this card, which may be older than the current one.
        var keyVersion = new CryptographyClient(new Uri(encrypted.KeyId), credential);
        var unwrapped = await keyVersion.UnwrapKeyAsync(
            KeyWrapAlgorithm.RsaOaep256, Convert.FromBase64String(encrypted.WrappedKey), cancellationToken);
        var dataKey = unwrapped.Key;
        try
        {
            var blob = Convert.FromBase64String(encrypted.Ciphertext);
            var nonce = blob.AsSpan(0, NonceSize);
            var ciphertext = blob.AsSpan(NonceSize, blob.Length - NonceSize - TagSize);
            var tag = blob.AsSpan(blob.Length - TagSize);
            var plaintext = new byte[ciphertext.Length];
            using (var aes = new AesGcm(dataKey, TagSize))
            {
                aes.Decrypt(nonce, ciphertext, tag, plaintext);
            }
            return Encoding.UTF8.GetString(plaintext);
        }
        finally
        {
            CryptographicOperations.ZeroMemory(dataKey);
        }
    }
}
