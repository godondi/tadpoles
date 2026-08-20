pipeline {
    agent any
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Build Image') {
            steps {
                sh 'mvn -B clean package -DskipTests'
                sh 'docker build -t tadpole-app:latest .'
            }
        }
        stage('Smoke Test') {
            steps {
                sh 'docker run --rm tadpole-app:latest'
            }
        }
        stage('Archive') {
            timeout(time: 2, unit: 'MINUTES') {
                input {
                    message "Do you want to archive the artifacts?"
                    ok "Yes"
                    cancel "No"
                }
            }
            steps {
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }
    }
}
