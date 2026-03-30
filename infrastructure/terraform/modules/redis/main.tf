resource "aws_elasticache_subnet_group" "this" {
  name       = "${var.name}-redis-subnets"
  subnet_ids = var.subnet_ids

  tags = merge(var.tags, {
    Name = "${var.name}-redis-subnets"
  })
}

resource "aws_security_group" "this" {
  name        = "${var.name}-redis-sg"
  description = "Security group for Redis"
  vpc_id      = var.vpc_id

  ingress {
    description = "Redis ingress"
    from_port   = 6379
    to_port     = 6379
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
    Name = "${var.name}-redis-sg"
  })
}

resource "aws_elasticache_replication_group" "this" {
  replication_group_id       = "${var.name}-redis"
  description                = "Redis replication group for FreightFlow"
  engine                     = "redis"
  engine_version             = var.engine_version
  node_type                  = var.node_type
  number_cache_clusters      = var.number_cache_clusters
  port                       = 6379
  subnet_group_name          = aws_elasticache_subnet_group.this.name
  security_group_ids         = [aws_security_group.this.id]
  multi_az_enabled           = true
  automatic_failover_enabled = true
  at_rest_encryption_enabled = var.at_rest_encryption_enabled
  transit_encryption_enabled = var.transit_encryption_enabled
  auto_minor_version_upgrade = true

  tags = merge(var.tags, {
    Name = "${var.name}-redis"
  })
}
