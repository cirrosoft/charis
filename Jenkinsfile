Instances.steps = this
Remote.steps = this
Docker.steps = this
node {
    def build = [
            projectName : "charis-ballet",
            env : "development",
            buildNumber : BUILD_NUMBER,
            instanceName : "charis-ballet-docker",
            instanceType : "t1.micro",
            instanceImage : "ami-1853ac65",
            instanceSecurityGroup : "ssh-http",
            instanceKeyPair : "deployment",
            commitHash : "",      // define after checkout (dac)
            commitHashFull : "",  // dac
            dockerName : "",      // dac
            dockerTag : "",       // dac
            awsCredential : "deployment"
    ]
    stage("Checkout") {
        def b = lastSuccessfullBuild();
        echo "BUILD START"
        echo b.displayName
        echo b.number
        echo "BUILD END"
        echo "Checkout Code Repository"
        def scmVars = checkout scm
        build.commitHashFull = scmVars.GIT_COMMIT
        build.commitHash = build.commitHashFull.substring(0, 6)
        build.dockerName = "${build.projectName}"
        build.dockerTag = "${build.buildNumber}-${build.commitHash}"
    }

    stage("Test") {
        //sh(script: "./gradlew test")
    }

    stage("Build") {
        sh(script: "./gradlew assemble")
        Docker.buildCleanImageAsLatest(build.dockerName, "latest-image.tar", [build.dockerTag])
    }

    def instanceIds
    stage("Infrastructure") {
        if (Instances.instanceExists(build.instanceName)) {
            instanceIds = Instances.getInstanceIds(build.instanceName)
        } else {
            def instanceId = Instances.createInstance(build.instanceName, build.instanceType, build.instanceImage, build.instanceSecurityGroup, build.instanceKeyPair)
            instanceIds = [instanceId]
            Instances.waitForInstance(instanceId)
            def ip = Instances.getInstancePublicIP(instanceId)
            def commands = [
                    "uname -a",
                    "sudo yum update -y",
                    "sudo yum install docker -y",
                    "sudo service docker start",
                    "sudo usermod -a -G docker ec2-user"
            ]
            Remote.executeRemoteCommands(build.awsCredential, ip, commands)
        }
    }

    stage("Deploy") {
        for (id in instanceIds) {
            def ip = Instances.getInstancePublicIP(id)
            Remote.executeRemoteCommands(build.awsCredential, ip, ["rm -rf latest-image.tar"]) // remove previous tar
            Remote.scp(build.awsCredential, ip, "latest-image.tar", "latest-image.tar") // deploy new tar
            // Stop and cleanup old containers
            def runningContainers = Remote.executeRemoteCommands(build.awsCredential, ip, ["docker ps -a -q --filter=\"ancestor=${build.dockerName}:latest\""])
            runningContainers?.trim()?.eachLine {
                Remote.executeRemoteCommands(build.awsCredential, ip, ["docker stop ${it}"])
                Remote.executeRemoteCommands(build.awsCredential, ip, ["docker rm ${it}"])
            }
            // Deploy new container
            def commands = [
                    "docker image load -i latest-image.tar",
                    "sudo docker run -d -p \"80:8080\" ${build.dockerName}:latest"
            ]
            Remote.executeRemoteCommands(build.awsCredential, ip, commands)
        }
        echo "Service Deployed"
    }
}

def lastSuccessfullBuild() {
    def b = currentBuild
    while (b != null) {
        echo "ITERATION BUILD NUMBER"
        echo "" + b.number
        b = build?.getPreviousBuild()
        if (b.result == 'SUCCESS') {
            return b;
        }
    }
    return null;
}

def getPreviousBlueGreen() {
    echo "JOB NAME:"
    echo JOB_NAME
    currentBuild.rawBuild.project
    if(!hudson.model.Result.SUCCESS.equals(currentBuild.rawBuild.getPreviousBuild()?.getResult())) {
        echo "last build failed"
    } else {
        echo "last build suceeded"
    }
    currentBuild.rawBuild.getPreviousBuild()
}

class Instances {
    public static def steps
    static String createInstance(String nameTag, String type, String ami, String securityGroup, String keyPairName) {
        nameTag = nameTag.replaceAll(' ', '-')
        steps.sh(script: """aws ec2 run-instances --image-id ${ami} --count 1 --instance-type ${type} --key-name ${keyPairName} --security-groups ${securityGroup} --tag-specifications ResourceType=instance,Tags=[\\{Key=Name,Value=${nameTag}\\}] > instance.out > instance.out""", returnStdout: true).trim()
        def result = steps.readFile 'instance.out'
        steps.sh """rm instance.out"""
        def regex = /InstanceId.*?(i-.*?)",/
        def match = (result =~ regex)
        match.find()
        def instanceId = match.group(1)
        return instanceId
    }

    static String[] getInstanceIds(nameTag) {
        nameTag = nameTag.replaceAll(" ", "-")

        steps.sh(script: """aws ec2 describe-instances --filters 'Name=tag:Name,Values=${nameTag}' 'Name=instance-state-name,Values=running' > instances.out""", returnStdout: true)
        def result = steps.readFile 'instances.out'
        steps.sh """rm instances.out"""
        def regex = /InstanceId.*?(i-.*?)",/
        def match = (result =~ regex)
        def instances = []
        while (match.find()) {
            def instanceId = match.group(1)
            instances.add(instanceId)
        }
        return instances
    }

    static boolean instanceExists(String nameTag) {
        def instances = this.getInstanceIds(nameTag)
        if (instances) {
            return true
        } else {
            return false
        }
    }

    static void deleteInstance(String id) {
        steps.sh(script: """aws ec2 terminate-instances --instance-ids ${id}""")
    }

    static void deleteInstances(String nameTag) {
        nameTag = nameTag.replaceAll(" ", "-")
        def instances = this.getInstanceIds(nameTag)
        for (id in instances) {
            this.deleteInstance(id)
        }
    }

    static void waitForInstance(String id) {
        steps.echo "Waiting for instance to start..."
        steps.sh(script: """aws ec2 wait instance-running --instance-ids ${id}""")
        steps.echo "Waiting for instance to become available to the network..."
        steps.sh(script: """aws ec2 wait instance-status-ok --instance-ids ${id}""")
    }

    static String getInstancePublicIP(String id) {
        return steps.sh(script: """aws ec2 describe-instances --instance-ids ${id} | grep PublicIpAddress | awk -F ":" '{print \$2}' | sed 's/[",]//g'""", returnStdout: true)
    }

    static String getInstancePrivateIP(String id) {
        return steps.sh(script: """aws ec2 describe-instances --instance-ids ${id} | grep PrivateIpAddress  | head -1 | awk -F ":" '{print \$2}' | sed 's/[",]//g'""", returnStdout: true)
    }

}

class Remote {
    public static def steps
    static String executeRemoteCommands(String credentialId, String address, ArrayList commands) {
        def lastResult = ""
        address = address.trim()
        steps.withCredentials([steps.sshUserPrivateKey(credentialsId: credentialId, keyFileVariable: 'SSH_KEYFILE', passphraseVariable: 'SSH_PASSWORD', usernameVariable: 'SSH_USERNAME')]) {
            for (command in commands) {
                steps.sh """
            ssh -i ${steps.SSH_KEYFILE} -o StrictHostKeyChecking=no -tt ${steps.SSH_USERNAME}@${address} ${command} > ssh-output.out
            """
                def result = steps.readFile 'ssh-output.out'
                result = result?.trim();
                steps.echo result
                lastResult = result
                steps.sh """rm ssh-output.out"""
            }
        }
        return lastResult
    }

    static void scp(String credentialId, String address, String fromPath, String toPath) {
        address = address.trim()
        steps.withCredentials([steps.sshUserPrivateKey(credentialsId: credentialId, keyFileVariable: 'SSH_KEYFILE', passphraseVariable: 'SSH_PASSWORD', usernameVariable: 'SSH_USERNAME')]) {
            steps.sh """
           scp -i ${steps.SSH_KEYFILE} -B ${fromPath} ${steps.SSH_USERNAME}@${address}:${toPath}
           """
        }
    }

}

class Docker {
    public static def steps

    static void buildCleanImageAsLatest(String imageName, String filename, ArrayList additionalImageTags = []) {
        def tagsString = ""
        for (tag in additionalImageTags) {
            tagsString += "-t " + imageName + ":" + tag + " "
        }
        steps.sh(script: "docker build ${tagsString} -t ${imageName}:latest .")
        steps.sh(script: "docker image save ${imageName}:latest > ${filename}")
        steps.sh(script: "docker rmi -f `docker images -a -q --filter=reference=\"${imageName}:*\"`")
    }

}


