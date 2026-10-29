// An Entra External ID tenant (CIAM directory): the users, app registrations and user flows of one
// CyVault environment. Deployed once per tenant, outside azd, into the resource group that holds
// the tenants (cyvault-rg, next to the production tenant cyvault.onmicrosoft.com):
//
//   az deployment group create -g cyvault-rg -f infra/tenant/tenant.bicep -p infra/tenant/debug.bicepparam
//
// azd never deletes it; delete a tenant in the Entra admin center after removing what's in it.

@description('Domain prefix: "cyvaultdebug" becomes cyvaultdebug.onmicrosoft.com. Must be unused in all of Entra.')
param domainPrefix string

@description('Name shown in the Entra admin center and on sign-in pages.')
param displayName string

@description('Two-letter country of the organization; it decides where the data is kept and can\'t be changed later.')
param countryCode string

@allowed(['United States', 'Europe', 'Asia Pacific', 'Australia', 'Japan'])
@description('Data location; the same as the production tenant.')
param location string = 'Europe'

resource tenant 'Microsoft.AzureActiveDirectory/ciamDirectories@2023-05-17-preview' = {
  name: '${domainPrefix}.onmicrosoft.com'
  location: location
  sku: {
    name: 'Base'
    tier: 'A0'
  }
  properties: {
    createTenantProperties: {
      displayName: displayName
      countryCode: countryCode
    }
  }
}

output tenantId string = tenant.properties.tenantId
output domainName string = tenant.properties.domainName
// The sign-in host the app's MSAL config and the Function App's AzureAd__Instance use.
output instance string = 'https://${domainPrefix}.ciamlogin.com/'
