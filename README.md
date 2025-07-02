# Smart Search One Stack

A secure, distroless Docker implementation that consolidates three Smart Search microservices into a single container for simplified deployment and enhanced security.

## 🏗️ Architecture

This project packages three Java microservices into a single distroless Docker container:

- **Search Admin** (Port 9080) - Administrative interface and user management
- **Smart Search API** (Port 9081) - Core search functionality with neural search capabilities  
- **Smart Search Util** (Port 9085) - Utility services and background processing

## 🔒 Security Features

- **Distroless Base Image**: Uses GoogleContainerTools distroless images eliminating shell access and debugging tools
- **No Source Code Exposure**: Prevents container introspection and source code access
- **Docker Secrets Integration**: Sensitive credentials managed via Docker secrets
- **Environment Variable Security**: Sensitive data separated from configuration files
- **Updated Dependencies**: All security vulnerabilities addressed with latest package versions

## 🚀 Quick Start

### Prerequisites

- Docker and Docker Compose
- Java 21 (for building from source)
- Maven 3.6+ (for building from source)

### Using Pre-built Images

```bash
# Pull the latest secure image
docker pull weblinktechs2021/ss-one-stack:secure-latest

# Run with Docker Compose
docker-compose -f docker-compose.distroless.yaml up
```

### Building from Source

1. **Build Individual Services**:
```bash
# Build search-admin
cd ../search-admin
mvn clean package -DskipTests
cp target/search-admin-3.0.7-SNAPSHOT.jar ../smart-search-one-stack/Search-Admin/target/

# Build smart-search-api  
cd ../smart-search-api
mvn clean package -DskipTests
cp target/smart-search-api-3.0.7-SNAPSHOT.jar ../smart-search-one-stack/SmartSearchAPI/target/

# Build smart-search-util
cd ../Smart-Search-Util-Service
mvn clean package -DskipTests
cp target/smart-search-util-3.0.7-SNAPSHOT.jar ../smart-search-one-stack/SmartSearchUtilservice/target/
```

2. **Build Docker Image**:
```bash
cd smart-search-one-stack
docker build -f Dockerfile.distroless -t weblinktechs2021/ss-one-stack:local .
```

3. **Run Application**:
```bash
docker-compose -f docker-compose.distroless.yaml up --build
```

## 🛠️ Configuration

### Environment Files

The application uses multiple environment files for security:

- `.env` - Basic configuration (committed to repo)
- `.env.config` - Non-sensitive application settings  
- `.env.secrets` - Sensitive credentials (not committed)

### Docker Secrets

Highly sensitive data is managed via Docker secrets:

- `jwt_secret` - JWT signing key
- `license_product_data` - License information
- `aws_secret_key` - AWS credentials
- `password_encryption_key` - Password encryption key

### Required Environment Variables

Create `.env.secrets` file with:

```bash
# Elasticsearch
ELASTIC_PASSWORD=your_elastic_password
INTERNAL_SEARCH_ENGINE_PASSWORD=your_elastic_password

# MongoDB (Internal Container)
MONGODB_URI=mongodb://admin:adminpass@mongodb:27017/ss-test-onestack?authSource=admin&retryWrites=true&w=majority

# AWS Configuration
AWS_CREDENTIALS_ACCESS_KEY=your_aws_access_key
AWS_BUCKET=your_s3_bucket

# License Configuration
LICENSE_KEY=your_license_key
LICENSE_USER_ID=your_user_id
LICENSE_CRYPTLEX_SERVICE_TOKEN=your_service_token

# Additional sensitive configurations...
```

## 🐳 Docker Images

### Available Tags

- `weblinktechs2021/ss-one-stack:secure-latest` - Latest secure build
- `weblinktechs2021/ss-one-stack:secure-3.0.7-v2` - Specific version with security fixes
- `weblinktechs2021/ss-one-stack:distroless-latest` - Latest distroless implementation

### Image Details

- **Base Image**: `gcr.io/distroless/java21-debian12`
- **Size**: Optimized for minimal attack surface
- **Architecture**: Multi-platform support (linux/amd64, linux/arm64)
- **Security**: No shell, package managers, or debugging tools

## 🔧 Services

### Internal Dependencies

The stack includes internal containerized services:

- **MongoDB 7.0** (Port 27018) - Primary database
- **Redis 7.2** (Port 6380) - Caching and session storage  
- **Elasticsearch 8.14.3** (Port 9200) - Search engine with security enabled

### Health Checks

All services include health checks for reliable startup:

- Elasticsearch: Authentication endpoint verification
- MongoDB: Connection and authentication testing
- Application services: Spring Boot actuator endpoints

## 📊 API Documentation

### Smart Search API

The Smart Search API provides:

- **Neural Search**: Advanced AI-powered search capabilities
- **Conversational Search**: Natural language query processing
- **Document Management**: Upload, index, and search documents
- **User Management**: Authentication and authorization
- **Analytics**: Search analytics and reporting

### Endpoints

- **Admin Interface**: `http://localhost:9080/auth/`
- **API Documentation**: `http://localhost:9081/swagger-ui.html`
- **Health Check**: `http://localhost:9080/actuator/health`

## 🔍 Development

### Local Development Setup

1. **Clone Required Repositories**:
```bash
git clone https://github.com/Weblink-Technologies-LLC/smart-search-one-stack.git
git clone https://github.com/Weblink-Technologies-LLC/search-admin.git
git clone https://github.com/Weblink-Technologies-LLC/smart-search-api.git
git clone https://github.com/Weblink-Technologies-LLC/Smart-Search-Util-Service.git
```

2. **Configure Environment**:
```bash
cp .env.example .env.secrets
# Edit .env.secrets with your configuration
```

3. **Start Development Environment**:
```bash
docker-compose -f docker-compose.distroless.yaml up --build
```

### Debugging

Since the distroless image has no shell access, debugging requires:

- **Log Analysis**: Check `/tmp/` directory for service logs
- **External Monitoring**: Use external APM tools
- **Health Endpoints**: Monitor Spring Boot actuator endpoints

## 🛡️ Security

### Vulnerability Management

This implementation addresses multiple security vulnerabilities:

- **CVE-2025-48734**: commons-beanutils upgraded to 1.11.0
- **CVE-2025-24813**: Tomcat upgraded via Spring Boot 3.5.3
- **Jackson CVEs**: Jackson 2.19.1 addresses multiple vulnerabilities
- **Container Security**: Distroless base eliminates attack vectors

### Security Best Practices

- **No Shell Access**: Distroless container prevents shell-based attacks
- **Secrets Management**: Docker secrets for sensitive data
- **Network Isolation**: Internal Docker network for service communication
- **Minimal Dependencies**: Only essential packages included
- **Regular Updates**: Automated dependency scanning and updates

## 📋 Troubleshooting

### Common Issues

1. **Service Startup Failures**:
   - Check Elasticsearch is healthy before starting application
   - Verify MongoDB connection string and credentials
   - Ensure all required environment variables are set

2. **Port Conflicts**:
   - Default ports: 9080, 9081, 9085, 9200, 27018, 6380
   - Modify docker-compose.yaml if ports are in use

3. **Memory Issues**:
   - Elasticsearch requires minimum 2GB RAM
   - Adjust `MEM_LIMIT` in environment configuration

### Log Locations

- **Application Logs**: `/tmp/` directory in container
- **Docker Logs**: `docker logs <container_name>`
- **Service Logs**: Individual service log files in mounted volumes

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make changes following existing patterns
4. Test with distroless build
5. Submit pull request with security considerations

## 📄 License

This project is proprietary software owned by Weblink Technologies LLC.

## 🔗 Related Repositories

- [Search Admin Service](https://github.com/Weblink-Technologies-LLC/search-admin)
- [Smart Search API](https://github.com/Weblink-Technologies-LLC/smart-search-api)  
- [Smart Search Util Service](https://github.com/Weblink-Technologies-LLC/Smart-Search-Util-Service)

## 📞 Support

For technical support and questions:
- **Email**: support@weblinktechs.com
- **Documentation**: [Smart Search Documentation](https://docs.smartsearchcloud.com)
- **Issues**: GitHub Issues in this repository

---

**Version**: 3.0.7  
**Last Updated**: July 2025  
**Maintained by**: Weblink Technologies LLC
