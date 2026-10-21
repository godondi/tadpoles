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
        stage('Frontend Suite') {
            steps {
                sh 'docker run --rm --user "$(id -u):$(id -g)" -e npm_config_cache=/tmp/npm -v "$PWD":/workspace -w /workspace/frontend node:22-bookworm-slim sh -c "npm ci && npm test -- --watch=false && npm run test:qof-styles && npm run build"'
            }
        }
        stage('Compose Startup and Upgrade') {
            steps {
                sh 'python3 scripts/test-client-compose.py'
                sh 'python3 scripts/test-operations-demo.py'
                sh 'python3 scripts/test-hardened-config.py'
                sh 'python3 scripts/test-client-recovery.py'
            }
        }
        stage('Archive Backend Artifact') {
            steps {
                archiveArtifacts artifacts: 'backend/target/*.jar', fingerprint: true
            }
        }
    }
}
