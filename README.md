# Tadpoles

## Team Member Names
- Devon: Dates
- Grace: Grapes
- Michael: Mayo
- Timothy: Tacos
- Justin: Jalapenos


## Team Git Workflow Choice
For our project, we opted to use Git-Flow Development, for the purpose of maintaining a clean and organized codebase. This workflow allows us to manage feature development, bug fixes, and releases in a structured manner.

We will impose a rule that all pull requests must be reviewed by at least one other team member and pass all Jenkins testing within pipeline.

## User Levels + Permissions

# Admin
- Administer users and manage system settings.
- Access to all data within the application.

# Auditor
- View and analyze data for auditing purposes.
- Access to audit logs and reports.

# Analyst
- View analytics dashboard for user activity and trends.

# Advisor
- Can manage the portfolio of their assigned clients.
- View client data and provide recommendations.

# Client
- Access to their own data and portfolio information.
- View reports and recommendations provided by their advisor.

## Database Integration Testing
- Schema under test: `database/schema/tadpoles_schema.sql`
- Test suite location: `testing/src/test/java/com/neueda/leap/db`
- Local run (requires Docker daemon for Testcontainers):
  - `cd testing && mvn -B test`
- Containerized run:
  - `docker compose --profile testing run --rm testing`
