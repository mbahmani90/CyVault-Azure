using Azure.Identity;
using Azure.Monitor.OpenTelemetry.Exporter;
using Azure.Storage.Blobs;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.Azure.Functions.Worker.Builder;
using Microsoft.Azure.Functions.Worker.OpenTelemetry;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Identity.Web;
using OpenTelemetry;

var builder = FunctionsApplication.CreateBuilder(args);

builder.ConfigureFunctionsWebApplication();

if (!string.IsNullOrEmpty(Environment.GetEnvironmentVariable("APPLICATIONINSIGHTS_CONNECTION_STRING")))
{
    builder.Services.AddOpenTelemetry()
        .UseFunctionsWorkerDefaults()
        .UseAzureMonitorExporter();
}

// Validates the user's Entra External ID access token (signature, issuer, audience, expiry).
// The functions call it explicitly through CallerAuthentication, because the Functions host
// doesn't run ASP.NET Core's [Authorize] middleware.
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddMicrosoftIdentityWebApi(builder.Configuration.GetSection("AzureAd"));

builder.Services.AddSingleton(_ => CreateAvatarContainer(builder.Configuration.GetSection("AvatarStorage")));

builder.Build().Run();

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
