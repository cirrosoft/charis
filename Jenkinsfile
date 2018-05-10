node {
    def build = [
            projectName : "charis-ballet",
            env : "development",
            buildNumber : BUILD_NUMBER,
            instanceName : "charis-ballet-docker",
            commitHash : "",
            commitHashFull : "",
            dockerName : "",
            dockerTag : "",
            dockerTagLatest : "",
            awsCredential : "deployment"
    ]
    stage("Checkout") {
        echo "Checkout Code Repository"
        def scmVars = checkout scm
        build.commitHashFull = scmVars.GIT_COMMIT
        build.commitHash = build.commitHashFull.substring(0, 6)
        build.dockerName = "${build.projectName}"
        build.dockerTag = "${build.dockerName}:${build.buildNumber}-${build.commitHash}"
        build.dockerTagLatest = "${build.dockerName}:latest"
    }

    stage("Test") {
        //sh(script: "./gradlew test")
    }

    stage("Build") {
        sh(script: "./gradlew assemble")
        sh(script: "docker build -t ${build.dockerTag} -t ${build.dockerTagLatest} .")
        sh(script: "docker image save ${build.dockerTagLatest} > latest-image.tar")
    }

    def instanceIds
    stage("Infrastructure") {
        if (instanceExists(build.instanceName)) {
            instanceIds = Instances.getInstanceIds(build.instanceName)
        } else {
            def instanceId = Instances.createInstance(build.instanceName)
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
            Remote.executeRemoteCommands(build.awsCredential, ip, ["rm -rf latest-image.tar"])
            Remote.scp(build.awsCredential, ip, "latest-image.tar", "latest-image.tar")
            // Cleanup old containers
            def runningContainers = Remote.executeRemoteCommands(build.awsCredential, ip, ["docker ps -a -q --filter=\"ancestor=${build.dockerTagLatest}\""])
            echo "RUNNING CONTAINERS"
            echo runningContainers
            runningContainers?.trim()?.eachLine {
                Remote.executeRemoteCommands(build.awsCredential, ip, ["docker stop ${it}"])
                Remote.executeRemoteCommands(build.awsCredential, ip, ["docker rm ${it}"])
            }
            // Deploy new container
            def commands = [
                    "docker image load -i latest-image.tar",
                    "sudo docker run -d -p \"80:8080\" ${build.dockerTagLatest}"
            ]
            Remote.executeRemoteCommands(build.awsCredential, ip, commands)
        }
        echo "Service Deployed"
    }
}



class Instances {

    static String createInstance(String nameTag) {
        nameTag = nameTag.replaceAll(' ', '-')
        sh(script: """aws ec2 run-instances --image-id ami-1853ac65 --count 1 --instance-type t1.micro --key-name deployment --security-groups ssh-http --tag-specifications ResourceType=instance,Tags=[\\{Key=Name,Value=${nameTag}\\}] > instance.out > instance.out""", returnStdout: true).trim()
        def result = readFile 'instance.out'
        sh """rm instance.out"""
        def regex = /InstanceId.*?(i-.*?)",/
        def match = (result =~ regex)
        match.find()
        def instanceId = match.group(1)
        return instanceId
    }

    static String[] getInstanceIds(String nameTag) {
        nameTag = nameTag.replaceAll(" ", "-")
        sh(script: """aws ec2 describe-instances --filters 'Name=tag:Name,Values=${nameTag}' 'Name=instance-state-name,Values=running' > instances.out""", returnStdout: true).trim()
        def result = readFile 'instances.out'
        sh """rm instances.out"""
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
        def instances = getInstanceIds(nameTag)
        if (instances) {
            return true
        } else {
            return false
        }
    }

    static void deleteInstance(String id) {
        sh(script: """aws ec2 terminate-instances --instance-ids ${id}""")
    }

    static void deleteInstances(String nameTag) {
        nameTag = nameTag.replaceAll(" ", "-")
        def instances = getInstanceIds(nameTag)
        for (id in instances) {
            deleteInstance(id)
        }
    }

    static void waitForInstance(String id) {
        echo "Waiting for instance to start..."
        sh(script: """aws ec2 wait instance-running --instance-ids ${id}""")
        echo "Waiting for instance to become available to the network..."
        sh(script: """aws ec2 wait instance-status-ok --instance-ids ${id}""")
    }

    static String getInstancePublicIP(String id) {
        return sh(script: """aws ec2 describe-instances --instance-ids ${id} | grep PublicIpAddress | awk -F ":" '{print \$2}' | sed 's/[",]//g'""", returnStdout: true)
    }

    static String getInstancePrivateIP(String id) {
        return sh(script: """aws ec2 describe-instances --instance-ids ${id} | grep PrivateIpAddress  | head -1 | awk -F ":" '{print \$2}' | sed 's/[",]//g'""", returnStdout: true)
    }

}

class Remote {

    static String executeRemoteCommands(String credentialId, String address, String[] commands) {
        def lastResult = ""
        address = address.trim()
        withCredentials([sshUserPrivateKey(credentialsId: credentialId, keyFileVariable: 'SSH_KEYFILE', passphraseVariable: 'SSH_PASSWORD', usernameVariable: 'SSH_USERNAME')]) {
            for (command in commands) {
                sh """
            ssh -i ${SSH_KEYFILE} -o StrictHostKeyChecking=no -tt ${SSH_USERNAME}@${address} ${command} > ssh-output.out
            """
                def result = readFile 'ssh-output.out'
                result = result?.trim();
                echo result
                lastResult = result
                sh """rm ssh-output.out"""
            }
        }
        return lastResult
    }

    static void scp(String credentialId, String address, String fromPath, String toPath) {
        address = address.trim()
        withCredentials([sshUserPrivateKey(credentialsId: credentialId, keyFileVariable: 'SSH_KEYFILE', passphraseVariable: 'SSH_PASSWORD', usernameVariable: 'SSH_USERNAME')]) {
            sh """
           scp -i ${SSH_KEYFILE} -B ${fromPath} ${SSH_USERNAME}@${address}:${toPath}
           """
        }
    }

}


