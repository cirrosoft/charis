def createInstance(name) {
    name = name.replaceAll(' ', '-')
    sh(script: """aws ec2 run-instances --image-id ami-1853ac65 --count 1 --instance-type t1.micro --key-name deployment --security-groups ssh-http --tag-specifications ResourceType=instance,Tags=[\\{Key=Name,Value=${name}\\}] > instance.out > instance.out""", returnStdout: true).trim()
    def result = readFile 'instance.out'
    sh """rm instance.out"""
    def regex = /InstanceId.*?(i-.*?)",/
    def match = (result =~ regex)
    match.find()
    def instanceId = match.group(1)
    return instanceId
}

def getInstances(nameTag) {
    nameTag = nameTag.replaceAll(" ", "-")
    sh(script: """aws ec2 describe-instances --filters 'Name=tag:Name,Values=${nameTag}' 'Name=instance-state-name,Values=running' > instances.out""", returnStdout: true).trim()
    def result = readFile 'instances.out'
    echo result
    sh """rm instances.out"""
//    def regex = /ResourceId.*?(i-.*?)",/
    def regex = /InstanceId.*?(i-.*?)",/
    def match = (result =~ regex)
    def instances = []
    while (match.find()) {
        instanceId = match.group(1)
        echo instanceId
        instances.add(instanceId)
    }
    return instances
}

def instanceExists(nameTag) {
    instances = getInstances(nameTag)
    if (instances) {
        return true
    } else {
        return false
    }
}

def deleteInstance(id) {
    sh(script: """aws ec2 terminate-instances --instance-ids ${id}""")
}

def deleteInstances(nameTag) {
    nameTag = nameTag.replaceAll(" ", "-")
    def instances = getInstances(nameTag)
    for (id in instances) {
        deleteInstance(id)
    }
}

def waitForInstance(id) {
    echo "Waiting for instance to start..."
    sh(script: """aws ec2 wait instance-running --instance-ids ${id}""")
    echo "Waiting for instance to become available to the network..."
    sh(script: """aws ec2 wait instance-status-ok --instance-ids ${id}""")
}

def executeRemoteCommands(credentials, address, commands) {
    def lastResult = ""
    address = address.trim()
    withCredentials([sshUserPrivateKey(credentialsId: credentials, keyFileVariable: 'SSH_KEYFILE', passphraseVariable: 'SSH_PASSWORD', usernameVariable: 'SSH_USERNAME')]) {
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

def scp(credentials, address, from, to) {
    address = address.trim()
    withCredentials([sshUserPrivateKey(credentialsId: credentials, keyFileVariable: 'SSH_KEYFILE', passphraseVariable: 'SSH_PASSWORD', usernameVariable: 'SSH_USERNAME')]) {
        sh """
           scp -i ${SSH_KEYFILE} -B ${from} ${SSH_USERNAME}@${address}:${to}
           """
    }
}

def getInstancePublicIP(id) {
    return sh(script: """aws ec2 describe-instances --instance-ids ${id} | grep PublicIpAddress | awk -F ":" '{print \$2}' | sed 's/[",]//g'""", returnStdout: true)
}

def getInstancePrivateIP(id) {
    return sh(script: """aws ec2 describe-instances --instance-ids ${id} | grep PrivateIpAddress  | head -1 | awk -F ":" '{print \$2}' | sed 's/[",]//g'""", returnStdout: true)
}

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
            dockerTagLatest : ""
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

//    if (env.BRANCH_NAME == "master") {
//        stage("Save Image") {
//            echo "Saving Image"
//        }
//    }

    def instanceIds
    stage("Infrastructure") {
        if (instanceExists(build.instanceName)) {
            instanceIds = getInstances(build.instanceName)
        } else {
            def instanceId = createInstance(build.instanceName)
            instanceIds = [instanceId]
            waitForInstance(instanceId)
            def ip = getInstancePublicIP(instanceId)
            def commands = [
                    "uname -a",
                    "sudo yum update -y",
                    "sudo yum install docker -y",
                    "sudo service docker start",
                    "sudo usermod -a -G docker ec2-user"
            ]
            executeRemoteCommands("deployment", ip, commands)
        }
    }

    stage("Deploy") {
        for (id in instanceIds) {
            def ip = getInstancePublicIP(id)
            executeRemoteCommands("deployment", ip, ["rm -rf latest-image.tar"])
            scp("deployment", ip, "latest-image.tar", "latest-image.tar")
            // Cleanup old containers
            def runningContainers = executeRemoteCommands("deployment", ip, ["docker ps -a -q --filter=\"ancestor=${build.dockerTagLatest}\""])
            echo "RUNNING CONTAINERS"
            echo runningContainers
            runningContainers?.trim()?.eachLine {
                executeRemoteCommands("deployment", ip, ["docker stop ${it}"])
                executeRemoteCommands("deployment", ip, ["docker rm ${it}"])
            }
            // Deploy new container
            def commands = [
                    "docker image load -i latest-image.tar",
                    "sudo docker run -d -p \"80:8080\" ${build.dockerTagLatest}"
            ]
            executeRemoteCommands("deployment", ip, commands)
        }
        echo "Deploying Service"
    }

}
