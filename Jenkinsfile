pipeline {
    agent any
    options {
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
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
                    junit testResults: 'backend/target/surefire-reports/*.xml', allowEmptyResults: false
                    archiveArtifacts artifacts: 'backend/target/surefire-reports/*.xml', fingerprint: true, onlyIfSuccessful: false
                    archiveArtifacts artifacts: 'backend/target/site/jacoco/**', fingerprint: false, onlyIfSuccessful: false
                }
            }
        }
        stage('Frontend Full Suite') {
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm test -- --watch=false'
                    sh 'npm run test:qof-styles'
                    sh 'npm run build'
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'frontend/dist/**', fingerprint: false, onlyIfSuccessful: false
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
                    junit testResults: 'testing/target/surefire-reports/*.xml', allowEmptyResults: false
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

                                        export COMPOSE_PROJECT_NAME="tadpole-ci-${BUILD_NUMBER:-local}"
                                        export CI_ENV_FILE=".env.ci"
                                        cp .env.ci.template "$CI_ENV_FILE"

                                        docker-compose --env-file "$CI_ENV_FILE" -p "$COMPOSE_PROJECT_NAME" up -d --build

                                        for url in \
                                            http://localhost:8080/health \
                                            http://localhost:8080/login; do
                                            for i in $(seq 1 60); do
                                                if curl --fail --silent --show-error "$url" >/dev/null; then
                                                    break
                                                fi
                                                if [ "$i" -eq 60 ]; then
                                                    echo "Timed out waiting for $url"
                                                    exit 1
                                                fi
                                                sleep 2
                                            done
                                        done
                '''
            }
            post {
                always {
                    sh '''
                                                export COMPOSE_PROJECT_NAME="tadpole-ci-${BUILD_NUMBER:-local}"
                                                docker-compose --env-file .env.ci -p "$COMPOSE_PROJECT_NAME" ps || true
                                                docker-compose --env-file .env.ci -p "$COMPOSE_PROJECT_NAME" logs > compose.log 2>&1 || true
                                                docker-compose --env-file .env.ci -p "$COMPOSE_PROJECT_NAME" down -v --remove-orphans || true
                                                rm -f .env.ci
                    '''
                                        archiveArtifacts artifacts: 'compose.log', allowEmptyArchive: true, onlyIfSuccessful: false
                }
        }
        stage('Compose Startup and Upgrade') {
            steps {
                sh 'python3 scripts/test-client-compose.py'
                sh 'python3 scripts/test-hardened-config.py'
            }
        }
    }
}
