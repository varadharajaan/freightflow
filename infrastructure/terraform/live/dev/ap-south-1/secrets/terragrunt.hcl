include "root" {
  path = find_in_parent_folders("root.hcl")
}

locals {
  env = read_terragrunt_config(find_in_parent_folders("env.hcl"))
}

terraform {
  source = "../../../../modules/secrets"
}

inputs = merge(include.root.inputs, {
  name_prefix = "${local.env.locals.project}/${local.env.locals.environment}"
  secrets = {
    keycloak-client-secret = {
      description   = "Keycloak client secret for FreightFlow"
      secret_string = "REPLACE_IN_SECRETS_MANAGER"
    }
    jwt-signing-key = {
      description   = "JWT signing key for internal service tokens"
      secret_string = "REPLACE_IN_SECRETS_MANAGER"
    }
  }
})
