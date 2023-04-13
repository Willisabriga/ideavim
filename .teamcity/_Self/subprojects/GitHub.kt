package _Self.subprojects

import _Self.Constants
import _Self.IdeaVimBuildType
import _Self.vcsRoots.GitHubPullRequest
import jetbrains.buildServer.configs.kotlin.v2019_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs

object GitHub : Project({
  name = "Pull Requests checks"
  description = "Automatic checking of GitHub Pull Requests"

  buildType(Github("clean test", "Tests"))
})

class Github(command: String, desc: String) : IdeaVimBuildType({
  name = "GitHub Pull Requests $desc"
  description = "Test GitHub pull requests $desc"

  params {
    param("env.ORG_GRADLE_PROJECT_downloadIdeaSources", "false")
    param("env.ORG_GRADLE_PROJECT_ideaVersion", Constants.GITHUB_TESTS)
    param("env.ORG_GRADLE_PROJECT_instrumentPluginCode", "false")
  }

  vcs {
    root(DslContext.settingsRoot)

    checkoutMode = CheckoutMode.AUTO
    branchFilter = """
            +:refs/(pull/*)/head
            -:<default>
        """.trimIndent()
  }

  steps {
    gradle {
      tasks = command
      buildFile = ""
      enableStacktrace = true
    }
  }

  triggers {
    vcs {
      branchFilter = """
            +:refs/(pull/*)/head
            -:<default>
      """.trimIndent()
    }
  }

  features {
    pullRequests {
      provider = github {
        authType = token {
          token = "credentialsJSON:43afd6e5-6ad5-4d12-a218-cf1547717a7f"
        }
        filterTargetBranch = "refs/heads/master"
        filterAuthorRole = PullRequests.GitHubRoleFilter.EVERYBODY
      }
    }
    commitStatusPublisher {
      vcsRootExtId = "${GitHubPullRequest.id}"
      publisher = github {
        githubUrl = "https://api.github.com"
        authType = personalToken {
          token = "credentialsJSON:43afd6e5-6ad5-4d12-a218-cf1547717a7f"
        }
      }
      param("github_oauth_user", "AlexPl292")
    }
  }
})
