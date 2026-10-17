pipeline {
    agent any
    options {
        timestamps()
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Backend Full Suite') {
            steps {
                dir('backend') {
                    sh 'mvn -B clean verify -Djacoco.skip=false'
                }
            }
            post {
                always {
                    junit testResults: 'backend/target/surefire-reports/*.xml', allowEmptyResults: true
                    archiveArtifacts artifacts: 'backend/target/surefire-reports/*.xml', fingerprint: true, onlyIfSuccessful: false
                    archiveArtifacts artifacts: 'backend/target/site/jacoco/**', fingerprint: false, onlyIfSuccessful: false
                }
            }
        }
        stage('Database Integration Suite') {
            steps {
                sh 'docker build -t tadpole-testing:latest -f testing/Dockerfile .'
                sh 'docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD":/workspace -w /workspace/testing tadpole-testing:latest mvn -B test'
            }
            post {
                always {
                    junit testResults: 'testing/target/surefire-reports/*.xml', allowEmptyResults: true
                    archiveArtifacts artifacts: 'testing/target/surefire-reports/*.xml', fingerprint: true, onlyIfSuccessful: false
                }
            }
        }
        stage('Build Backend Image') {
            steps {
                sh 'docker build -t tadpole-app:latest -f backend/Dockerfile backend'
            }
        }
        stage('Archive Build Artifacts') {
            steps {
                archiveArtifacts artifacts: 'backend/target/*.jar', fingerprint: true
                archiveArtifacts artifacts: 'frontend/dist/**', fingerprint: false
            }
        }
        stage('Build Runtime Images') {
            steps {
                sh '''
                    set -eux
                    image_tag="${GIT_COMMIT:-${BUILD_TAG:-latest}}"
                    docker build -t "tadpole-app:${image_tag}" -t tadpole-app:latest -f backend/Dockerfile backend
                                        docker build -t "tadpole-frontend:${image_tag}" -t tadpole-frontend:latest -f frontend/Dockerfile frontend
                                        docker build -t "tadpole-auth-stub:${image_tag}" -t tadpole-auth-stub:latest -f auth-stub/Dockerfile auth-stub
                    docker build -t "tadpole-nginx:${image_tag}" -t tadpole-nginx:latest -f nginx/Dockerfile nginx
                '''
            }
        }

        stage('Compose Startup and Upgrade') {
            steps {
                sh 'python3 scripts/test-client-compose.py'
                sh 'python3 scripts/test-hardened-config.py'
            }
        }
        stage('Archive Backend Artifact') {
            steps {
                archiveArtifacts artifacts: 'backend/target/*.jar', fingerprint: true
            }
        }
        
        stage('Compose E2E Smoke Test') {
            steps {
                sh '''
                    set -eux

                    docker rm -f tadpole-smoke-app tadpole-smoke-db >/dev/null 2>&1 || true
                    docker network rm tadpole-smoke >/dev/null 2>&1 || true
                    docker network create tadpole-smoke

                    docker run -d \
                      --name tadpole-smoke-db \
                      --network tadpole-smoke \
                      -e POSTGRES_DB=tadpoles \
                      -e POSTGRES_USER=postgres \
                      -e POSTGRES_PASSWORD=postgres \
                      postgres:15

                    for i in $(seq 1 30); do
                      if docker exec tadpole-smoke-db pg_isready -U postgres -d tadpoles; then
                        break
                      fi
                      sleep 2
                    done

                    docker run -d \
                      --name tadpole-smoke-app \
                      --network tadpole-smoke \
                      -e DB_URL=jdbc:postgresql://tadpole-smoke-db:5432/tadpoles \
                      -e DB_USERNAME=postgres \
                      -e DB_PASSWORD=postgres \
                      -e JWT_SECRET=test-jwt-secret-test-jwt-secret-123456 \
                      tadpole-app:latest

                    for i in $(seq 1 30); do
                      if ! docker ps --format '{{.Names}}' | grep -q '^tadpole-smoke-app$'; then
                        docker logs tadpole-smoke-app || true
                        exit 1
                      fi

                      if docker logs tadpole-smoke-app 2>&1 | grep -q 'Started Main'; then
                        exit 0
                      fi

                      sleep 2
                    done

                    docker logs tadpole-smoke-app
                    exit 1
                '''
            }
            post {
                always {
                    sh '''
                        docker logs tadpole-smoke-app || true
                        docker logs tadpole-smoke-db || true
                        docker rm -f tadpole-smoke-app tadpole-smoke-db >/dev/null 2>&1 || true
                        docker network rm tadpole-smoke >/dev/null 2>&1 || true
                    '''
                }
            }
        }
        stage('Archive Backend Artifact') {
            steps {
                archiveArtifacts artifacts: 'backend/target/*.jar', fingerprint: true
            }
        }
    }
}
