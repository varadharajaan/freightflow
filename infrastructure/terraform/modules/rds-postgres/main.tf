resource "aws_db_subnet_group" "this" {
  name       = "${var.name}-subnet-group"
  subnet_ids = var.subnet_ids

  tags = merge(var.tags, {
    Name = "${var.name}-subnet-group"
  })
}

resource "aws_security_group" "this" {
  name        = "${var.name}-sg"
  description = "PostgreSQL access security group"
  vpc_id      = var.vpc_id

  ingress {
    description = "PostgreSQL ingress"
    from_port   = var.port
    to_port     = var.port
    protocol    = "tcp"
    cidr_blocks = var.allowed_cidr_blocks
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, {
    Name = "${var.name}-sg"
  })
}

resource "aws_db_instance" "this" {
  identifier                        = "${var.name}-postgres"
  engine                            = "postgres"
  engine_version                    = var.engine_version
  instance_class                    = var.instance_class
  db_name                           = var.db_name
  allocated_storage                 = var.allocated_storage
  max_allocated_storage             = var.max_allocated_storage
  storage_encrypted                 = true
  username                          = "freightflow_admin"
  manage_master_user_password       = true
  backup_retention_period           = var.backup_retention_period
  multi_az                          = var.multi_az
  deletion_protection               = var.deletion_protection
  skip_final_snapshot               = false
  final_snapshot_identifier         = "${var.name}-postgres-final"
  db_subnet_group_name              = aws_db_subnet_group.this.name
  vpc_security_group_ids            = [aws_security_group.this.id]
  enabled_cloudwatch_logs_exports   = ["postgresql", "upgrade"]
  performance_insights_enabled      = true
  auto_minor_version_upgrade        = true
  apply_immediately                 = false
  iam_database_authentication_enabled = true

  tags = merge(var.tags, {
    Name = "${var.name}-postgres"
  })
}
