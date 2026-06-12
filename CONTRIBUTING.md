# Contributing to TechDecide — Backend (techdecide-api)

Thank you for your interest in contributing to the TechDecide backend!

## Tech Stack

- Java 21 + Spring Boot 3.5
- Spring Security + JWT (stateless)
- Spring Data JPA + Hibernate
- PostgreSQL (Docker locally)
- Maven

## Local Setup

See [README.md](README.md) for the full local dev setup (Docker Postgres + `mvn spring-boot:run`).

## Branching Model

This project uses **GitFlow**:

| Branch pattern   | Purpose                              |
|------------------|--------------------------------------|
| `main`           | Latest stable release — protected    |
| `develop`        | Integration branch — protected       |
| `feature/<name>` | New features — branch from `develop` |
| `fix/<name>`     | Bug fixes — branch from `develop`    |

Never commit directly to `main` or `develop`.

```bash
git checkout develop
git checkout -b feature/your-feature-name
```

## Commit Style

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add supersede decision endpoint
fix: correct 403 on comment delete
chore: upgrade Spring Boot to 3.6
refactor: extract decision governance to service
test: add 400 case for illegal status transition
docs: update README with new env variable
```

## Layer Order

Always generate in this exact order — never skip or merge layers:

```
Controller → Service → Repository
```

- **Controllers**: HTTP only — validate input, delegate to service, return `ResponseEntity<DTO>`
- **Services**: all business logic, annotated `@Service @Transactional`, constructor injection only
- **Repositories**: Spring Data JPA, `@EntityGraph` on any finder that loads associations

## Pull Requests

- Open PRs against `develop`, not `main`
- Keep each PR focused on one concern
- Ensure `mvn test` passes with zero failures before opening a PR
- No entity returned directly from controllers — always map to DTO
- Authorization checks must compare by `id` (Long), never by name or email

## Reporting Issues

Open a GitHub Issue with:
- A clear description of the bug or feature request
- Steps to reproduce (for bugs)
- Expected vs actual behavior

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
