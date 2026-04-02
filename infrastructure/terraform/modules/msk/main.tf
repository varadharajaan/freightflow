resource "aws_security_group" "this" {
  name        = "${var.name}-msk-sg"
  description = "Security group for MSK brokers"
  vpc_id      = var.vpc_id

  ingress {
    description = "TLS Kafka ingress"
    from_port   = 9094
    to_port     = 9094
    protocol    = "tcp"
    cidr_blocks = var.allowed_cidr_blocks
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"] #tfsec:ignore:aws-ec2-no-public-egress-sgr
  }

  tags = merge(var.tags, {
    Name = "${var.name}-msk-sg"
  })
}

#tfsec:ignore:aws-msk-enable-at-rest-encryption
#tfsec:ignore:aws-msk-enable-logging
resource "aws_msk_cluster" "this" {
  cluster_name           = "${var.name}-msk"
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.number_of_broker_nodes

  broker_node_group_info {
    instance_type   = var.broker_instance_type
    client_subnets  = var.subnet_ids
    security_groups = [aws_security_group.this.id]

    storage_info {
      ebs_storage_info {
        volume_size = var.ebs_volume_size
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }

    encryption_at_rest_kms_key_arn = var.kms_key_arn
  }

  client_authentication {
    sasl {
      iam = true
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = var.cloudwatch_log_group != null
        log_group = var.cloudwatch_log_group
      }
    }
  }

  tags = merge(var.tags, {
    Name = "${var.name}-msk"
  })
}
