// CyVault backend: a Flex Consumption Function App that reaches its storage only through its
// system-assigned Managed Identity. No storage account has shared keys enabled.

param environmentName string
param location string
param tags object
param principalId string
param entraInstance string
param entraTenantId string
param entraApiClientId string

var token = toLower(uniqueString(subscription().id, resourceGroup().id, environmentName))
var avatarContainerName = 'avatars'
var balanceTableName = 'balances'
var deploymentContainerName = 'app-package'

// Built-in role definition IDs
var roles = {
  storageBlobDataOwner: 'b7e6dc6d-f1e8-4753-8033-0f276bb0955b'
  storageBlobDataContributor: 'ba92f5b4-2d11-453d-a403-e96b0029c9fe'
  storageQueueDataContributor: '974c5e8b-45b9-4653-ba55-5f855dd0fb88'
  storageTableDataContributor: '0a9a7e1f-b9d0-4cc4-a60d-0319b160aaa3'
}

// ---------- Monitoring ----------

resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: 'log-cyvault-${token}'
  location: location
  tags: tags
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
  }
}

resource appInsights 'Microsoft.Insights/components@2020-02-02' = {
  name: 'appi-cyvault-${token}'
  location: location
  tags: tags
  kind: 'web'
  properties: {
    Application_Type: 'web'
    WorkspaceResourceId: logs.id
  }
}

// ---------- Storage ----------

// The Functions runtime's own storage (host state, deployment package). Not user data.
resource runtimeStorage 'Microsoft.Storage/storageAccounts@2023-05-01' = {
  name: 'stcyvrt${token}'
  location: location
  tags: tags
  kind: 'StorageV2'
  sku: { name: 'Standard_LRS' }
  properties: {
    allowBlobPublicAccess: false
    allowSharedKeyAccess: false
    minimumTlsVersion: 'TLS1_2'
    supportsHttpsTrafficOnly: true
  }

  resource blobs 'blobServices' = {
    name: 'default'

    resource deployment 'containers' = {
      name: deploymentContainerName
      properties: { publicAccess: 'None' }
    }
  }
}

// User data: profile photos, one blob per user at avatars/{oid}/avatar.jpg, and the balances
// table, one partition per user (PartitionKey = oid).
resource avatarStorage 'Microsoft.Storage/storageAccounts@2023-05-01' = {
  name: 'stcyvav${token}'
  location: location
  tags: tags
  kind: 'StorageV2'
  sku: { name: 'Standard_LRS' }
  properties: {
    allowBlobPublicAccess: false
    allowSharedKeyAccess: false
    minimumTlsVersion: 'TLS1_2'
    supportsHttpsTrafficOnly: true
  }

  resource blobs 'blobServices' = {
    name: 'default'

    resource avatars 'containers' = {
      name: avatarContainerName
      properties: { publicAccess: 'None' }
    }
  }

  resource tableService 'tableServices' = {
    name: 'default'

    resource balances 'tables' = {
      name: balanceTableName
    }
  }
}

// ---------- Function App ----------

resource plan 'Microsoft.Web/serverfarms@2024-04-01' = {
  name: 'plan-cyvault-${token}'
  location: location
  tags: tags
  kind: 'functionapp'
  sku: {
    tier: 'FlexConsumption'
    name: 'FC1'
  }
  properties: {
    reserved: true // Linux
  }
}

resource functionApp 'Microsoft.Web/sites@2024-04-01' = {
  name: 'func-cyvault-${token}'
  location: location
  tags: union(tags, { 'azd-service-name': 'api' })
  kind: 'functionapp,linux'
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    siteConfig: {
      minTlsVersion: '1.2'
      ftpsState: 'Disabled'
    }
    functionAppConfig: {
      deployment: {
        storage: {
          type: 'blobContainer'
          value: '${runtimeStorage.properties.primaryEndpoints.blob}${deploymentContainerName}'
          authentication: {
            type: 'SystemAssignedIdentity'
          }
        }
      }
      scaleAndConcurrency: {
        maximumInstanceCount: 40
        instanceMemoryMB: 2048
      }
      runtime: {
        name: 'dotnet-isolated'
        version: '10.0'
      }
    }
  }

  resource settings 'config' = {
    name: 'appsettings'
    properties: {
      // Identity-based connection to the runtime storage: no connection string or key.
      AzureWebJobsStorage__accountName: runtimeStorage.name
      APPLICATIONINSIGHTS_CONNECTION_STRING: appInsights.properties.ConnectionString
      AzureAd__Instance: entraInstance
      AzureAd__TenantId: entraTenantId
      AzureAd__ClientId: entraApiClientId
      AvatarStorage__BlobServiceUri: avatarStorage.properties.primaryEndpoints.blob
      AvatarStorage__ContainerName: avatarContainerName
      BalanceStorage__TableServiceUri: avatarStorage.properties.primaryEndpoints.table
      BalanceStorage__TableName: balanceTableName
    }
    dependsOn: [
      runtimeBlobOwner
      runtimeQueueContributor
      runtimeTableContributor
    ]
  }
}

// ---------- Role assignments (IAM) ----------

// The Functions host needs these on its own runtime storage account.
resource runtimeBlobOwner 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(runtimeStorage.id, functionApp.id, roles.storageBlobDataOwner)
  scope: runtimeStorage
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageBlobDataOwner)
    principalId: functionApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

resource runtimeQueueContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(runtimeStorage.id, functionApp.id, roles.storageQueueDataContributor)
  scope: runtimeStorage
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageQueueDataContributor)
    principalId: functionApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

resource runtimeTableContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(runtimeStorage.id, functionApp.id, roles.storageTableDataContributor)
  scope: runtimeStorage
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageTableDataContributor)
    principalId: functionApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

// The Function may read and write blobs in the avatars container only, not the whole account.
resource avatarsContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(avatarStorage::blobs::avatars.id, functionApp.id, roles.storageBlobDataContributor)
  scope: avatarStorage::blobs::avatars
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageBlobDataContributor)
    principalId: functionApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

// Lets the developer run the Function locally against the real container through `az login`.
resource developerAvatarsContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (!empty(principalId)) {
  name: guid(avatarStorage::blobs::avatars.id, principalId, roles.storageBlobDataContributor)
  scope: avatarStorage::blobs::avatars
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageBlobDataContributor)
    principalId: principalId
    principalType: 'User'
  }
}

// The Function may read and write the balances table only, not the other tables in the account.
resource balancesContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(avatarStorage::tableService::balances.id, functionApp.id, roles.storageTableDataContributor)
  scope: avatarStorage::tableService::balances
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageTableDataContributor)
    principalId: functionApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

// Lets the developer run the Function locally against the real table through `az login`.
resource developerBalancesContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (!empty(principalId)) {
  name: guid(avatarStorage::tableService::balances.id, principalId, roles.storageTableDataContributor)
  scope: avatarStorage::tableService::balances
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageTableDataContributor)
    principalId: principalId
    principalType: 'User'
  }
}

output functionAppName string = functionApp.name
output apiBaseUrl string = 'https://${functionApp.properties.defaultHostName}/api'
output avatarBlobServiceUri string = avatarStorage.properties.primaryEndpoints.blob
