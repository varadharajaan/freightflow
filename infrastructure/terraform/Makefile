TF_ROOT := .

.PHONY: fmt validate tfsec

fmt:
	terraform fmt -recursive $(TF_ROOT)

validate:
	@echo "Run validation per stack with terragrunt:"
	@echo "cd live/dev/ap-south-1 && terragrunt run-all validate"

tfsec:
	tfsec $(TF_ROOT)
