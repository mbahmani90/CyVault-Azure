using SkiaSharp;

namespace CyVault.Api.Profile;

/// <summary>
/// Turns an uploaded file into a clean avatar: it must decode as a real image, is turned upright
/// from its EXIF orientation, downscaled to at most 512 px, and re-encoded as JPEG. Re-encoding
/// drops all metadata, including EXIF GPS location.
/// </summary>
public static class AvatarImage
{
    public const int MaxSide = 512;

    // Rejects decompression bombs: a small file that claims huge dimensions.
    private const int MaxSourceSide = 8000;

    private const int JpegQuality = 85;

    /// <returns>The JPEG bytes, or null when the input isn't a supported image.</returns>
    public static byte[]? Normalize(byte[] input)
    {
        using var codec = SKCodec.Create(new SKMemoryStream(input));
        if (codec is null) return null;

        var info = codec.Info;
        if (info.Width <= 0 || info.Height <= 0 || info.Width > MaxSourceSide || info.Height > MaxSourceSide) return null;

        // Skia fills in the missing part of a truncated file; a broken upload is rejected instead.
        using var source = new SKBitmap(new SKImageInfo(info.Width, info.Height, SKColorType.Rgba8888, SKAlphaType.Premul));
        if (codec.GetPixels(source.Info, source.GetPixels()) != SKCodecResult.Success) return null;

        var origin = codec.EncodedOrigin;
        var turned = origin is SKEncodedOrigin.LeftTop or SKEncodedOrigin.RightTop
            or SKEncodedOrigin.RightBottom or SKEncodedOrigin.LeftBottom;
        var uprightWidth = turned ? source.Height : source.Width;
        var uprightHeight = turned ? source.Width : source.Height;

        var scale = Math.Min(1f, (float)MaxSide / Math.Max(uprightWidth, uprightHeight));
        var width = Math.Max(1, (int)Math.Round(uprightWidth * scale));
        var height = Math.Max(1, (int)Math.Round(uprightHeight * scale));

        using var surface = SKSurface.Create(new SKImageInfo(width, height, SKColorType.Rgba8888, SKAlphaType.Premul));
        var canvas = surface.Canvas;
        canvas.Clear(SKColors.White); // JPEG has no transparency
        canvas.Scale(scale);
        canvas.Concat(UprightMatrix(origin, source.Width, source.Height));
        using (var sourceImage = SKImage.FromBitmap(source))
        {
            canvas.DrawImage(sourceImage, 0, 0, new SKSamplingOptions(SKCubicResampler.Mitchell));
        }

        using var result = surface.Snapshot();
        using var jpeg = result.Encode(SKEncodedImageFormat.Jpeg, JpegQuality);
        return jpeg?.ToArray();
    }

    /// <summary>Maps the stored pixels (w × h) onto the upright image, per the EXIF orientation.</summary>
    private static SKMatrix UprightMatrix(SKEncodedOrigin origin, float w, float h) => origin switch
    {
        SKEncodedOrigin.TopRight => new SKMatrix(-1, 0, w, 0, 1, 0, 0, 0, 1),     // mirrored
        SKEncodedOrigin.BottomRight => new SKMatrix(-1, 0, w, 0, -1, h, 0, 0, 1), // rotated 180°
        SKEncodedOrigin.BottomLeft => new SKMatrix(1, 0, 0, 0, -1, h, 0, 0, 1),   // flipped
        SKEncodedOrigin.LeftTop => new SKMatrix(0, 1, 0, 1, 0, 0, 0, 0, 1),       // transposed
        SKEncodedOrigin.RightTop => new SKMatrix(0, -1, h, 1, 0, 0, 0, 0, 1),     // needs 90° clockwise
        SKEncodedOrigin.RightBottom => new SKMatrix(0, -1, h, -1, 0, w, 0, 0, 1), // transverse
        SKEncodedOrigin.LeftBottom => new SKMatrix(0, 1, 0, -1, 0, w, 0, 0, 1),   // needs 90° counter-clockwise
        _ => SKMatrix.Identity,
    };
}
