# Local Deployment Guide

## Without Docker

### Database Setup
Start by making a database within PostgreSQL, the ideal name for this is `tadpoles`
but can be customized to your liking.

Connect to that database and run the following SQL files found in the `/database` directory to create the necessary 
tables:

- `/schema/revised_schema.sql` - This file contains the schema for the database, including tables, columns, and 
constraints.
- `/test_data/tadpoles_test_data.sql` - This file contains test data to populate the database for testing purposes.

Using pgAdmin or any other PostgreSQL client, you can execute these SQL files to set up the database. Then validate 
that the tables are created and populated.

### Backend Setup
Within the `backend` directory, start by right clicking the `pom.xml` file and select `Add as Maven Project`.
This allows IntelliJ to recognize the project as a Maven project and download the necessary dependencies.

Next, you want to go to the `src/main/resources/application_template.yml` file and rename/copy the file 
as `application.yaml`.

Then update the database connection properties to match your local PostgreSQL setup. This includes the database URL, 
username, and password. In theory, only the password should need to be changed, unless you run on a different port or 
renamed the database to something else.

Default port is `8080` and can be changed in the `application.yaml` file if needed.

To run the backend api itself:
```bash
mvn spring-boot:run
```

To run the JUnit test suite:
```bash
mvn -B clean test
```

## Using Docker

N/A