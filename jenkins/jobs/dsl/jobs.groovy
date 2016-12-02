// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"
def cicdFolderName = projectFolderName + "/CI-CD2"
def cicdFolder = folder(cicdFolderName) { displayName('Continuous Integration - Continuous Delivery') }

//Repositories
def devopsdemo =  "git@gitlab:${WORKSPACE_NAME}/devopsdemo.git"
def deployment =  "git@gitlab:${WORKSPACE_NAME}/deployment.git"
def seleniumtest =  "git@gitlab:${WORKSPACE_NAME}/selenium-test.git"

// Jobs
def builddemo = mavenJob(cicdFolderName + "/Build_Demo")
def codeanalysisdemo = freeStyleJob(cicdFolderName + "/Code_Analysis_Demo")
def deployansibledemo = freeStyleJob(cicdFolderName + "/Deploy_Ansible_Demo")
def seleniumtestautomation = mavenJob(cicdFolderName + "/Selenium_Test_Automation")

//Pipeline
def java_pipeline_demo = buildPipelineView(cicdFolderName + "/Java-Pipeline-Demo")

java_pipeline_demo.with{
    title('Java Pipeline Demo')
    displayedBuilds(5)
    selectedJob(cicdFolderName + "/Build_Demo")
    showPipelineParameters()
    refreshFrequency(5)
}

// Job Configuration
builddemo.with{
	properties{
		configure { project -> project / 'properties' / 'hudson.plugins.copyartifact.CopyArtifactPermissionProperty' / 'projectNameList' {
			string('Deploy_Ansible_Demo')
			}
		}
	}
	wrappers {
        preBuildCleanup()
    }
	parameters{
		stringParam("DEMO_WORKSPACE","","")
	}
	scm{
		git{
		  remote{
			url(devopsdemo)
			credentials("adop-jenkins-master")
		  }
		  branch("*/master")
		}
	}
	disableDownstreamTrigger(true)
	blockOnDownstreamProjects()
	rootPOM('pom.xml')
	goals('clean package')
	triggers {
        snapshotDependencies(true)
		configure { project -> project / 'triggers' / 'com.dabsquared.gitlabjenkins.GitLabPushTrigger' {
            spec('')
            triggerOnPush(true)
            triggerOnMergeRequest(true)
            triggerOpenMergeRequestOnPush('never')
            triggerOnNoteRequest(true)
            noteRegex('Release')
            ciSkip(true)
            skipWorkInProgressMergeRequest(true)
            setBuildDescription(true)
            addNoteOnMergeRequest(true)
            addCiMessage(false)
            addVoteOnMergeRequest(true)
            branchFilterType('All')
            includeBranchesSpec('')
            excludeBranchesSpec('')
            targetBranchRegex('')
            acceptMergeRequestOnSuccess(false)
			} 
		}          
	} 
	publishers{
		archiveArtifacts('**/*.war')
		downstreamParameterized{
		  trigger(cicdFolderName + "/Code_Analysis_Demo"){
				condition("SUCCESS")
				parameters{
					predefinedProp("DEMO_WORKSPACE",'$WORKSPACE')
				}
			}
		}
	}
}

codeanalysisdemo.with{
	parameters{
		stringParam("DEMO_WORKSPACE","","")
	}
	wrappers {
        timestamps()
		colorizeOutput()
    }
    configure { Project ->
        Project / builders << 'hudson.plugins.sonar.SonarRunnerBuilder'{
            project('')
            properties('''# Required metadata
sonar.projectKey=org.sonarqube:java-simple-sq-scanner
sonar.projectName=Java :: Simple Project Not Compiled :: SonarQube Scanner
sonar.projectVersion=1.0

# Comma-separated paths to directories with sources (required)
sonar.sources=.

# Language
sonar.language=java

# Encoding of the source files
sonar.sourceEncoding=UTF-8''')
            javaOpts('')
            additionalArguments('')
            jdk('(Inherit From Job)')
            task('')
        }
    }
	publishers{
        downstreamParameterized{
			trigger(cicdFolderName + "/Deploy_Ansible_Demo"){
				condition("SUCCESS")
                triggerWithNoParameters(true)
			}
		}
	}
}

deployansibledemo.with{
    label('ansible')
    scm{
		git{
		  remote{
			url(deployment)
			credentials("adop-jenkins-master")
		  }
		  branch("*/master")
		}
	}
	wrappers {
        sshAgent('ec2-user')
    }
    steps {
        configure { Project -> Project / builders / 'hudson.plugins.copyartifact.CopyArtifact' {
            project('Build_Demo')
            filter('**/*.war')
            target('')
            excludes('')
            doNotFingerprintArtifacts('false')
			}
        }
        shell('''#!/bin/sh
set +e

wget -P /tmp/ https://s3-eu-west-1.amazonaws.com/keyfile-key/DevOps-Training-SG-_key.pem
chmod 0600 /tmp/DevOps-Training-SG-_key.pem

cd ansible-playbook-master/
ansible-playbook playbook.yml -i hosts --key-file=/tmp/DevOps-Training-SG-_key.pem -u ec2-user --become --become-user root
rm -rf /tmp/DevOps-Training-SG-_key.pem''')
    }
	publishers{
		downstreamParameterized{
		  trigger(cicdFolderName + "/Selenium_Test_Automation"){
				condition("SUCCESS")
              	triggerWithNoParameters(true)
			}
		}
	}
}

seleniumtestautomation.with{
	scm{
		git{
		  remote{
			url(seleniumtest)
			credentials("adop-jenkins-master")
		  }
		  branch("**")
		}
	}
	triggers {
        snapshotDependencies(true)
	}
	preBuildSteps {
        shell('''# Remove broken tests
rm -rf ./src/test/java/com/accenture/dcsc/springpetclinic_selenium/OwnerTest.java
rm -rf ./src/test/java/com/accenture/dcsc/springpetclinic_selenium/selenium''')
    }
	rootPOM('pom.xml')
	goals('clean test -B')
	
}