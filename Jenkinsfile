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
                sh 'mvn -B clean verify -DskipTests'    // verify performs an additional step after package
                sh 'docker build -t tadpole-app:latest .'
            }
        }
        stage('Smoke Test') {
            steps {
                sh 'docker run --rm tadpole-app:latest'
            }
        }
        stage('Code Coverage') {
            steps {
                sh 'mvn jacoco:report'
            }
        }
        stage('Archive') {
            steps {
                timeout(time: 2, unit: 'MINUTES') {
                    input message: 'Do you want to archive the artifacts?', ok: 'Yes'
                }
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }
    }
}
