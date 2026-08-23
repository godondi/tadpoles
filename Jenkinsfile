pipeline {
    agent any

    // Parameters exposed in the Jenkins UI when starting a build
    parameters {
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip Maven tests')
        choice(name: 'TARGET_ENV', choices: "dev\nstaging\nprod", description: 'Target build environment')
        booleanParam(name: 'SKIP_SMOKE_TEST', defaultValue: false, description: 'Skip running the smoke test container')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Image') {
            steps {
                script {
                    // Build the mvn command conditionally based on the SKIP_TESTS parameter
                    def skipArg = params.SKIP_TESTS ? '-DskipTests' : ''

                    echo "Building for environment: ${params.TARGET_ENV}"
                    sh "mvn -B clean verify -DskipTests ${skipArg}"

                    // Pass the TARGET_ENV as a build-arg and tag the image with the environment
                    sh "docker build --build-arg ENV=${params.TARGET_ENV} -t tadpole-app:${params.TARGET_ENV} ."
                }
            }
        }

        stage('Parallel Test') {
            parallel {
                stage('Smoke Test') {
                    // Only run this stage if SKIP_SMOKE_TEST is false
                    when {
                        expression { return !params.SKIP_SMOKE_TEST }
                    }
                    steps {
                        // Run the image tagged for the selected environment
                        sh "docker run --rm tadpole-app:${params.TARGET_ENV}"
                    }
                }

                stage('Code Coverage') {
                    steps {
                        sh 'mvn jacoco:report'
                    }
                }
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

    post {
        always {
            echo "Pipeline finished for TARGET_ENV=${params.TARGET_ENV}, SKIP_TESTS=${params.SKIP_TESTS}, SKIP_SMOKE_TEST=${params.SKIP_SMOKE_TEST}"
        }
    }
}
