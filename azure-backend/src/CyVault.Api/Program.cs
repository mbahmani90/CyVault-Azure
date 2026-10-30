using Azure.Core;
using Azure.Data.Tables;
using Azure.Identity;
using Azure.Monitor.OpenTelemetry.Exporter;
using Azure.Storage.Blobs;
using CyVault.Api.Session;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.Azure.Functions.Worker.Builder;
using Microsoft.Azure.Functions.Worker.OpenTelemetry;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
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

builder.Services.AddHttpClient();
builder.Services.AddSingleton(sp => new SignInSessions(
    sp.GetRequiredService<IHttpClientFactory>(),
    CreateGraphCredential(builder.Configuration.GetSection("AzureAd"), builder.Configuration.GetSection("Graph"))));

builder.Build().Run();

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
