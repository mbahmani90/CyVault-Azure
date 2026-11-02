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
var cardTableName = 'cards'
var phoneTableName = 'phones'
var transferTableName = 'transfers'
var deviceTableName = 'devices'
var challengeTableName = 'challenges'
var cardKeyName = 'card-key'
var deploymentContainerName = 'app-package'

// Built-in role definition IDs
var roles = {
  storageBlobDataOwner: 'b7e6dc6d-f1e8-4753-8033-0f276bb0955b'
  storageBlobDataContributor: 'ba92f5b4-2d11-453d-a403-e96b0029c9fe'
  storageQueueDataContributor: '974c5e8b-45b9-4653-ba55-5f855dd0fb88'
  storageTableDataContributor: '0a9a7e1f-b9d0-4cc4-a60d-0319b160aaa3'
  keyVaultCryptoUser: '12338af0-0e69-4776-bea7-57ae8d297424'
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

// User data: profile photos, one blob per user at avatars/{oid}/avatar.jpg; the balances and
// cards tables, one partition per user (PartitionKey = oid); and the phones table, which maps a
// phone number to the card it's linked to (PartitionKey = phone).
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

    resource cards 'tables' = {
      name: cardTableName
    }

    resource phones 'tables' = {
      name: phoneTableName
    }

    resource transfers 'tables' = {
      name: transferTableName
    }

    resource devices 'tables' = {
      name: deviceTableName
    }

    resource challenges 'tables' = {
      name: challengeTableName
    }
  }
}

// ---------- Card encryption ----------

// Holds card-key, the RSA key that wraps each card's AES data key (envelope encryption, see
// CardEncryption). Access is by Azure RBAC role only, no access policies.
resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: 'kv-cyvault-${token}'
  location: location
  tags: tags
  properties: {
    tenantId: subscription().tenantId
    sku: { family: 'A', name: 'standard' }
    enableRbacAuthorization: true
    enableSoftDelete: true
    softDeleteRetentionInDays: 90
  }

  // Its private key never leaves the vault: the Function wraps with the public key and asks the
  // vault to unwrap.
  resource cardKey 'keys' = {
    name: cardKeyName
    properties: {
      kty: 'RSA'
      keySize: 2048
      keyOps: ['wrapKey', 'unwrapKey']
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
      CardStorage__TableServiceUri: avatarStorage.properties.primaryEndpoints.table
      CardStorage__CardsTableName: cardTableName
      CardStorage__PhonesTableName: phoneTableName
      TransferStorage__TableServiceUri: avatarStorage.properties.primaryEndpoints.table
      TransferStorage__TableName: transferTableName
      DeviceStorage__TableServiceUri: avatarStorage.properties.primaryEndpoints.table
      DeviceStorage__TableName: deviceTableName
      ChallengeStorage__TableServiceUri: avatarStorage.properties.primaryEndpoints.table
      ChallengeStorage__TableName: challengeTableName
      // Without a version, so new cards are always wrapped with card-key's current version.
      CardEncryption__KeyId: keyVault::cardKey.properties.keyUri
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

// The Function may read and write the cards and phones tables, each granted on its own.
resource cardsContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(avatarStorage::tableService::cards.id, functionApp.id, roles.storageTableDataContributor)
  scope: avatarStorage::tableService::cards
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageTableDataContributor)
    principalId: functionApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

resource phonesContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(avatarStorage::tableService::phones.id, functionApp.id, roles.storageTableDataContributor)
  scope: avatarStorage::tableService::phones
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageTableDataContributor)
    principalId: functionApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

// Lets the developer run the Function locally against the real tables through `az login`.
resource developerCardsContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (!empty(principalId)) {
  name: guid(avatarStorage::tableService::cards.id, principalId, roles.storageTableDataContributor)
  scope: avatarStorage::tableService::cards
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageTableDataContributor)
    principalId: principalId
    principalType: 'User'
  }
}

resource developerPhonesContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (!empty(principalId)) {
  name: guid(avatarStorage::tableService::phones.id, principalId, roles.storageTableDataContributor)
  scope: avatarStorage::tableService::phones
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageTableDataContributor)
    principalId: principalId
    principalType: 'User'
  }
}

// The Function may read and write the transfers table: one row per transfer, under the sender.
resource transfersContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(avatarStorage::tableService::transfers.id, functionApp.id, roles.storageTableDataContributor)
  scope: avatarStorage::tableService::transfers
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageTableDataContributor)
    principalId: functionApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

resource developerTransfersContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (!empty(principalId)) {
  name: guid(avatarStorage::tableService::transfers.id, principalId, roles.storageTableDataContributor)
  scope: avatarStorage::tableService::transfers
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageTableDataContributor)
    principalId: principalId
    principalType: 'User'
  }
}

// The Function may read and write the devices table: one row per enrolled phone, under its owner.
resource devicesContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(avatarStorage::tableService::devices.id, functionApp.id, roles.storageTableDataContributor)
  scope: avatarStorage::tableService::devices
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageTableDataContributor)
    principalId: functionApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

resource developerDevicesContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (!empty(principalId)) {
  name: guid(avatarStorage::tableService::devices.id, principalId, roles.storageTableDataContributor)
  scope: avatarStorage::tableService::devices
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageTableDataContributor)
    principalId: principalId
    principalType: 'User'
  }
}

// The Function may read and write the challenges table: one row per transfer challenge, and each
// user's failed-signature count, under the sender.
resource challengesContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(avatarStorage::tableService::challenges.id, functionApp.id, roles.storageTableDataContributor)
  scope: avatarStorage::tableService::challenges
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageTableDataContributor)
    principalId: functionApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

resource developerChallengesContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (!empty(principalId)) {
  name: guid(avatarStorage::tableService::challenges.id, principalId, roles.storageTableDataContributor)
  scope: avatarStorage::tableService::challenges
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.storageTableDataContributor)
    principalId: principalId
    principalType: 'User'
  }
}

// The Function may wrap and unwrap with the vault's keys, nothing more (no reading secrets or
// managing keys).
resource keyVaultCryptoUser 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(keyVault.id, functionApp.id, roles.keyVaultCryptoUser)
  scope: keyVault
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.keyVaultCryptoUser)
    principalId: functionApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

// Lets the developer run the Function locally against the real vault through `az login`.
resource developerKeyVaultCryptoUser 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (!empty(principalId)) {
  name: guid(keyVault.id, principalId, roles.keyVaultCryptoUser)
  scope: keyVault
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.keyVaultCryptoUser)
    principalId: principalId
    principalType: 'User'
  }
}

// For the federated credential that lets the Function call Graph as the CyVault API app.
output functionPrincipalId string = functionApp.identity.principalId

output functionAppName string = functionApp.name
output apiBaseUrl string = 'https://${functionApp.properties.defaultHostName}/api'
output avatarBlobServiceUri string = avatarStorage.properties.primaryEndpoints.blob
