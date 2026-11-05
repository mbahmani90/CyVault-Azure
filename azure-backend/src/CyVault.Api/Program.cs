using Azure.Core;
using Azure.Data.Tables;
using Azure.Identity;
using Azure.Monitor.OpenTelemetry.Exporter;
using Azure.Storage.Blobs;
using CyVault.Api.Balance;
using CyVault.Api.Card;
using CyVault.Api.Device;
using CyVault.Api.Push;
using CyVault.Api.Session;
using CyVault.Api.Transfer;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.Azure.Functions.Worker.Builder;
using Microsoft.Azure.Functions.Worker.OpenTelemetry;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Identity.Web;
using OpenTelemetry;
using OpenTelemetry.Trace;

var builder = FunctionsApplication.CreateBuilder(args);

builder.ConfigureFunctionsWebApplication();

if (!string.IsNullOrEmpty(Environment.GetEnvironmentVariable("APPLICATIONINSIGHTS_CONNECTION_STRING")))
{
    // The Azure SDK only emits its traces when this switch is on. With it, every Blob and Table
    // Storage call shows up as a dependency in Application Insights.
    AppContext.SetSwitch("Azure.Experimental.EnableActivitySource", true);

    builder.Services.AddOpenTelemetry()
        .UseFunctionsWorkerDefaults()
        .WithTracing(tracing => tracing.AddSource("Azure.*"))
        .UseAzureMonitorExporter();
}

// Validates the user's Entra External ID access token (signature, issuer, audience, expiry).
// The functions call it explicitly through CallerAuthentication, because the Functions host
// doesn't run ASP.NET Core's [Authorize] middleware.
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddMicrosoftIdentityWebApi(builder.Configuration.GetSection("AzureAd"));

builder.Services.AddSingleton(_ => CreateAvatarContainer(builder.Configuration.GetSection("AvatarStorage")));
builder.Services.AddSingleton(_ => CreateBalanceTable(builder.Configuration.GetSection("BalanceStorage")));
builder.Services.AddSingleton<BalanceWriter>();
builder.Services.AddSingleton(_ => CreateCardTables(builder.Configuration.GetSection("CardStorage")));
builder.Services.AddSingleton(_ => CreateCardEncryption(builder.Configuration.GetSection("CardEncryption")));
builder.Services.AddSingleton(_ => CreateTransferTable(builder.Configuration.GetSection("TransferStorage")));
builder.Services.AddSingleton(_ => CreateDeviceTable(builder.Configuration.GetSection("DeviceStorage")));
builder.Services.AddSingleton(_ => CreateChallengeTable(builder.Configuration.GetSection("ChallengeStorage")));
builder.Services.AddSingleton(_ => CreatePushTokenTable(builder.Configuration.GetSection("PushTokenStorage")));
builder.Services.AddSingleton<TransferSigning>();

builder.Services.AddHttpClient();
builder.Services.AddSingleton(sp =>
{
    var httpClients = sp.GetRequiredService<IHttpClientFactory>();
    var options = CreatePushOptions(builder.Configuration.GetSection("Push"));
    var googleToken = options is null
        ? null
        : new GoogleAccessToken(httpClients, new ManagedIdentityCredential(ManagedIdentityId.SystemAssigned), options);
    return new PushNotifications(
        httpClients, sp.GetRequiredService<PushTokenTable>(), googleToken, options,
        sp.GetRequiredService<ILogger<PushNotifications>>());
});
builder.Services.AddSingleton(sp => new SignInSessions(
    sp.GetRequiredService<IHttpClientFactory>(),
    CreateGraphCredential(builder.Configuration.GetSection("AzureAd"), builder.Configuration.GetSection("Graph"))));

builder.Build().Run();

// Google only trusts the Function App's Managed Identity, so without a project (local runs) no
// push is sent.
static PushOptions? CreatePushOptions(IConfigurationSection config)
{
    var projectId = config["ProjectId"];
    if (string.IsNullOrEmpty(projectId)) return null;

    string Required(string key) => config[key] ?? throw new InvalidOperationException($"Set Push:{key}.");
    return new PushOptions(
        projectId,
        Required("ProjectNumber"),
        config["PoolId"] ?? "azure",
        config["ProviderId"] ?? "cyvault",
        Required("ServiceAccount"),
        Required("Audience"));
}

// Graph is called as the CyVault API app registration in the External ID tenant.
static TokenCredential CreateGraphCredential(IConfigurationSection azureAd, IConfigurationSection graph)
{
    var tenantId = azureAd["TenantId"] ?? throw new InvalidOperationException("Set AzureAd:TenantId.");
    var clientId = azureAd["ClientId"] ?? throw new InvalidOperationException("Set AzureAd:ClientId.");

    // Local development: a client secret of the app registration, kept in local.settings.json.
    var clientSecret = graph["ClientSecret"];
    if (!string.IsNullOrEmpty(clientSecret))
    {
        return new ClientSecretCredential(tenantId, clientId, clientSecret);
    }

    // In Azure there is no secret: the app registration trusts the Function App's Managed
    // Identity through a federated credential, and the identity's token is the client assertion.
    var managedIdentity = new ManagedIdentityCredential(ManagedIdentityId.SystemAssigned);
    return new ClientAssertionCredential(tenantId, clientId, async cancellationToken =>
        (await managedIdentity.GetTokenAsync(
            new TokenRequestContext(["api://AzureADTokenExchange/.default"]), cancellationToken)).Token);
}

static TableClient CreateBalanceTable(IConfigurationSection config)
{
    var tableName = config["TableName"] ?? "balances";

    // Local development against Azurite. In Azure the Managed Identity is used, and Bicep
    // creates the table.
    var connectionString = config["ConnectionString"];
    if (!string.IsNullOrEmpty(connectionString))
    {
        var local = new TableClient(connectionString, tableName);
        local.CreateIfNotExists();
        return local;
    }

    var serviceUri = config["TableServiceUri"]
        ?? throw new InvalidOperationException("Set BalanceStorage:TableServiceUri or BalanceStorage:ConnectionString.");
    return new TableClient(new Uri(serviceUri), tableName, new DefaultAzureCredential());
}

static CardTables CreateCardTables(IConfigurationSection config)
{
    var cardsTableName = config["CardsTableName"] ?? "cards";
    var phonesTableName = config["PhonesTableName"] ?? "phones";

    // Local development against Azurite. In Azure the Managed Identity is used, and Bicep
    // creates the tables.
    var connectionString = config["ConnectionString"];
    if (!string.IsNullOrEmpty(connectionString))
    {
        var cards = new TableClient(connectionString, cardsTableName);
        var phones = new TableClient(connectionString, phonesTableName);
        cards.CreateIfNotExists();
        phones.CreateIfNotExists();
        return new CardTables(cards, phones);
    }

    var serviceUri = new Uri(config["TableServiceUri"]
        ?? throw new InvalidOperationException("Set CardStorage:TableServiceUri or CardStorage:ConnectionString."));
    var credential = new DefaultAzureCredential();
    return new CardTables(
        new TableClient(serviceUri, cardsTableName, credential),
        new TableClient(serviceUri, phonesTableName, credential));
}

static TransferTable CreateTransferTable(IConfigurationSection config)
{
    var tableName = config["TableName"] ?? "transfers";

    // Local development against Azurite. In Azure the Managed Identity is used, and Bicep
    // creates the table.
    var connectionString = config["ConnectionString"];
    if (!string.IsNullOrEmpty(connectionString))
    {
        var local = new TableClient(connectionString, tableName);
        local.CreateIfNotExists();
        return new TransferTable(local);
    }

    var serviceUri = config["TableServiceUri"]
        ?? throw new InvalidOperationException("Set TransferStorage:TableServiceUri or TransferStorage:ConnectionString.");
    return new TransferTable(new TableClient(new Uri(serviceUri), tableName, new DefaultAzureCredential()));
}

static ChallengeTable CreateChallengeTable(IConfigurationSection config)
{
    var tableName = config["TableName"] ?? "challenges";

    // Local development against Azurite. In Azure the Managed Identity is used, and Bicep
    // creates the table.
    var connectionString = config["ConnectionString"];
    if (!string.IsNullOrEmpty(connectionString))
    {
        var local = new TableClient(connectionString, tableName);
        local.CreateIfNotExists();
        return new ChallengeTable(local);
    }

    var serviceUri = config["TableServiceUri"]
        ?? throw new InvalidOperationException("Set ChallengeStorage:TableServiceUri or ChallengeStorage:ConnectionString.");
    return new ChallengeTable(new TableClient(new Uri(serviceUri), tableName, new DefaultAzureCredential()));
}

static PushTokenTable CreatePushTokenTable(IConfigurationSection config)
{
    var tableName = config["TableName"] ?? "pushtokens";

    // Local development against Azurite. In Azure the Managed Identity is used, and Bicep
    // creates the table.
    var connectionString = config["ConnectionString"];
    if (!string.IsNullOrEmpty(connectionString))
    {
        var local = new TableClient(connectionString, tableName);
        local.CreateIfNotExists();
        return new PushTokenTable(local);
    }

    var serviceUri = config["TableServiceUri"]
        ?? throw new InvalidOperationException("Set PushTokenStorage:TableServiceUri or PushTokenStorage:ConnectionString.");
    return new PushTokenTable(new TableClient(new Uri(serviceUri), tableName, new DefaultAzureCredential()));
}

static DeviceTable CreateDeviceTable(IConfigurationSection config)
{
    var tableName = config["TableName"] ?? "devices";

    // Local development against Azurite. In Azure the Managed Identity is used, and Bicep
    // creates the table.
    var connectionString = config["ConnectionString"];
    if (!string.IsNullOrEmpty(connectionString))
    {
        var local = new TableClient(connectionString, tableName);
        local.CreateIfNotExists();
        return new DeviceTable(local);
    }

    var serviceUri = config["TableServiceUri"]
        ?? throw new InvalidOperationException("Set DeviceStorage:TableServiceUri or DeviceStorage:ConnectionString.");
    return new DeviceTable(new TableClient(new Uri(serviceUri), tableName, new DefaultAzureCredential()));
}

// The Managed Identity in Azure; locally, the developer's `az login`, which Bicep gives the same
// role on the vault. There's no local Key Vault emulator, so local runs use the real vault.
static CardEncryption CreateCardEncryption(IConfigurationSection config)
{
    var keyId = config["KeyId"] ?? throw new InvalidOperationException("Set CardEncryption:KeyId to card-key's URI.");
    return new CardEncryption(new Uri(keyId), new DefaultAzureCredential());
}

static BlobContainerClient CreateAvatarContainer(IConfigurationSection config)
{
    var containerName = config["ContainerName"] ?? "avatars";

    // Local development against Azurite. In Azure there is no connection string: the
    // Function App's Managed Identity is used, and Bicep creates the container.
    var connectionString = config["ConnectionString"];
    if (!string.IsNullOrEmpty(connectionString))
    {
        var local = new BlobContainerClient(connectionString, containerName);
        local.CreateIfNotExists();
        return local;
    }

    var serviceUri = config["BlobServiceUri"]
        ?? throw new InvalidOperationException("Set AvatarStorage:BlobServiceUri or AvatarStorage:ConnectionString.");
    return new BlobServiceClient(new Uri(serviceUri), new DefaultAzureCredential())
        .GetBlobContainerClient(containerName);
}
