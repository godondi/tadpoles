pipeline {
    agent any
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Build Backend') {
            steps {
                dir('backend') {
                    sh 'mvn -B clean verify'
                }
            }
        }
        stage('Build Image') {
            steps {
                sh 'docker build -t tadpole-app:latest -f backend/Dockerfile backend'
            }
        }
        stage('Smoke Test') {
            steps {
                sh 'docker run --rm tadpole-app:latest'
            }
        }
        stage('Code Coverage') {
            steps {
                dir('backend') {
                    sh 'mvn jacoco:report'
                }
            }
        }
        stage('Database Integration Tests') {
            steps {
                sh 'docker build -t tadpole-testing:latest -f testing/Dockerfile .'
                sh 'docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD":/workspace -w /workspace/testing tadpole-testing:latest mvn -B test'
                junit testResults: 'testing/target/surefire-reports/*.xml'
                archiveArtifacts artifacts: 'testing/target/surefire-reports/*.xml', fingerprint: true
            }
        }
        stage('Archive') {
            steps {
                timeout(time: 2, unit: 'MINUTES') {
                    input message: 'Do you want to archive the artifacts?', ok: 'Yes'
                }
                archiveArtifacts artifacts: 'backend/target/*.jar', fingerprint: true
            }
        }
    }
}
