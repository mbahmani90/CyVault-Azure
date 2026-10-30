// The debug environment's tenant: its own users, separate from production's.
using 'tenant.bicep'

param domainPrefix = 'cyvaultdebug'
param displayName = 'CyVault Debug'
// Like the production tenant (cyvault.onmicrosoft.com).
param countryCode = 'PT'
param location = 'Europe'
