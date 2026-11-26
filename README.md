# HelpDesk
HelpDesk is a web application for raising, tracking and resolving maintenance and service requests in a gated community.
Residents report issues (a leaking pipe, a power outage, a broken lift) as tickets, which are assigned to staff members who work on them until they are resolved.
Administrators oversee the whole process and use analytics to monitor support performance.

![HelpDesk: track tickets, manage customers, monitor performance and provide great support](docs/assets/helpdesk-banner.png)

## Prerequisites
* JDK 25
* Docker and Docker Compose

## Docs
- [Project Overview](docs/project.md)
- [Tech Stack](docs/tech-stack.md)
- [Requirements](docs/requirements.md)

## Local Development
This project uses [mise](https://mise.jdx.dev/) to install the required SDKs and run common tasks defined in `mise.toml`.

Install mise by following the [installation guide](https://mise.jdx.dev/getting-started.html) (ex: `brew install mise`).

```shell
# Trust the project's mise.toml (one-time)
$ mise trust

# Install the SDKs (Java 25 Temurin, Maven)
$ mise install

# List available tasks
$ mise tasks

# Format code and run tests
$ mise run format
$ mise run test

# Run verification checks
$ mise run verify

# Build docker image
$ mise run build_image

# Run application in docker container
$ mise run start
$ mise run stop
$ mise run restart
```
