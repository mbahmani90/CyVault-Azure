// Entry point for `azd up`: creates the resource group and everything in it.
targetScope = 'subscription'

@minLength(1)
@maxLength(32)
@description('azd environment name, e.g. "dev". Used in resource names.')
param environmentName string

@minLength(1)
@description('Azure region for all resources.')
param location string

@description('Object ID of the developer running azd. Gets blob access to the avatars container for local runs against Azure. Empty to skip.')
param principalId string = ''

@description('Entra External ID sign-in host, e.g. https://cyvault.ciamlogin.com/')
param entraInstance string

@description('Entra External ID tenant ID (GUID).')
param entraTenantId string

@description('Client ID of the "CyVault API" app registration in the External ID tenant.')
param entraApiClientId string

var tags = { 'azd-env-name': environmentName }

resource rg 'Microsoft.Resources/resourceGroups@2024-03-01' = {
  name: 'rg-cyvault-${environmentName}'
  location: location
  tags: tags
}

module resources 'resources.bicep' = {
  name: 'resources'
  scope: rg
  params: {
    environmentName: environmentName
    location: location
    tags: tags
    principalId: principalId
    entraInstance: entraInstance
    entraTenantId: entraTenantId
    entraApiClientId: entraApiClientId
  }
}

output AZURE_RESOURCE_GROUP string = rg.name
output AZURE_FUNCTION_APP_NAME string = resources.outputs.functionAppName
output API_BASE_URL string = resources.outputs.apiBaseUrl
output AVATAR_STORAGE_BLOB_URI string = resources.outputs.avatarBlobServiceUri
