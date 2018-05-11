ProjectTools.steps = this
Instances.steps = this
Route53.steps = this
Remote.steps = this
Docker.steps = this
node {
    def build = [
            appName : "charis-ballet",
            color : "grey",
            buildNumber : BUILD_NUMBER,
            instanceName : "charis-ballet", // dac
            instanceType : "t1.micro",
            instanceImage : "ami-1853ac65",
            instanceSecurityGroup : "ssh-http",
            instanceKeyPair : "deployment",
            commitHash : "",      // define after checkout (dac)
            commitHashFull : "",  // dac
            dockerName : "",      // dac
            dockerTag : "",       // dac
            awsCredential : "deployment",
            domainName : "charisballet.com."
    ]
    stage("\u265A Checkout") {
        build.color = ProjectTools.getBlueOrGreen()
        echo "Deployment Color:"
        echo build.color
        echo "Checkout Code Repository"
        build.instanceName = "${build.instanceName}-${build.color}"
        def scmVars = checkout scm
        build.commitHashFull = scmVars.GIT_COMMIT
        build.commitHash = build.commitHashFull.substring(0, 6)
        build.dockerName = "${build.appName}"
        build.dockerTag = "${build.buildNumber}-${build.commitHash}"
    }

    stage("\u267A Unit Test") {
        //sh(script: "./gradlew test")
    }

    stage("\u2692 Build") {
        sh(script: "./gradlew assemble")
        Docker.buildCleanImageAsLatest(build.dockerName, "latest-image.tar", [build.dockerTag])
    }

    def instanceIds
    stage("\u26E9 Infrastructure") {
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

    stage("\u26A1 Deploy") {
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
            def javaParams = ProjectTools.generateJavaPropertiesString(build)
            def commands = [
                    "docker image load -i latest-image.tar",
                    "sudo docker run -e JAVA_OPTS=\\\"${javaParams}\\\" -d -p \"80:8080\" ${build.dockerName}:latest"
            ]
            Remote.executeRemoteCommands(build.awsCredential, ip, commands)
        }
        echo "Service Deployed"
    }

    def ip
    stage("\u267A Integration Test") {
        for (id in instanceIds) {
            ip = Instances.getInstancePublicIP(id)
            Remote.waitForUrlSuccess("http://${ip}/health")
        }
    }

    stage("\u21C6 Crossover") {
        def zoneId = Route53.getHostedZoneId(build.domainName)
        Route53.createRecord(zoneId, build.domainName, ip)
    }


}

class ProjectTools {
    public static def steps
    static ArrayList getSuccessfullBuilds() {
        def b = steps.currentBuild
        def builds = []
        while (b != null) {
            b = b?.getPreviousBuild()
            if (b?.result == 'SUCCESS') {
                builds.add(b)
            }
        }
        return builds;
    }
    static String getBlueOrGreen() {
        def builds = getSuccessfullBuilds()
        def count = builds.size()
        if (count % 2 == 0) {
            return "blue"
        } else {
            return "green"
        }
    }
    static String generateJavaPropertiesString(Map props) {
        def propsString = ""
        props.each{ k, v ->
            propsString += "-Dbuild."+k+"="+v+" "
        }
        return propsString
    }
}


class Instances {
    public static def steps
    static String createInstance(String nameTag, String type, String ami, String securityGroup, String keyPairName) {
        nameTag = nameTag.replaceAll(' ', '-')
        steps.sh(script: """aws ec2 run-instances --image-id ${ami} --count 1 --instance-type ${type} --key-name ${keyPairName} --security-groups ${securityGroup} --tag-specifications ResourceType=instance,Tags=[\\{Key=Name,Value=${nameTag}\\}] | tee instance.out""", returnStdout: true).trim()
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

        steps.sh(script: """aws ec2 describe-instances --filters 'Name=tag:Name,Values=${nameTag}' 'Name=instance-state-name,Values=running' | tee instances.out""", returnStdout: true)
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
        return steps.sh(script: """aws ec2 describe-instances --instance-ids ${id} | grep PublicIpAddress | awk -F ":" '{print \$2}' | sed 's/[",]//g'""", returnStdout: true).trim()
    }

    static String getInstancePrivateIP(String id) {
        return steps.sh(script: """aws ec2 describe-instances --instance-ids ${id} | grep PrivateIpAddress  | head -1 | awk -F ":" '{print \$2}' | sed 's/[",]//g'""", returnStdout: true).trim()
    }

}

class Route53 {
    public static def steps
    static String getHostedZoneId(String domainName) {
        steps.sh("""aws route53 list-hosted-zones | jq '.HostedZones[] | select(.Name==\\\"${domainName}\\\")' | tee zones.out""", returnStdout: true)
        def result = steps.readFile 'zones.out'
        steps.sh """rm zones.out"""
        def regex = /Id.*?\/hostedzone\/(.*?)",/
        def match = (result =~ regex)
        if (match.find()) {
            def zone = match.group(1)
            return zone
        }
        return null
    }
    static String createRecord(String zoneId, String domainName, String ip) {
        def record = """
        {
            "Comment": "A new record set for the zone.",
            "Changes": [
                {
                    "Action": "UPSERT",
                    "ResourceRecordSet": {
                    "Name": "${domainName}",
                    "Type": "A",
                    "TTL": 60,
                    "ResourceRecords": [
                            {
                                "Value": "${ip}"
                            }
                    ]
                }
                }
        ]
        }
        """
        writeFile(file: "dsn-record.json", text: record)
        sh(script: """aws route53 change-resource-record-sets --hosted-zone-id ${zoneId} --change-batch file://dns-record.json | tee change.out""", returnStdout: true)
        def result = steps.readFile 'change.out'
        steps.sh """rm change.out"""
        steps.sh """rm dns-record.json"""
        def regex = /Id.*?\/change\/(.*?)",/
        def match = (result =~ regex)
        if (match.find()) {
            def changeId = match.group(1)
            return changeId
        }
        return null
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
            ssh -i ${steps.SSH_KEYFILE} -o StrictHostKeyChecking=no -tt ${steps.SSH_USERNAME}@${address} ${command} | tee ssh-output.out
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

    static void waitForUrlSuccess(String url) {
        steps.timeout(5) {
            steps.waitUntil {
                steps.script {
                    steps.echo "Waiting for response from ${url}"
                    def result = steps.sh(script: "wget -q ${url} -O /dev/null", returnStatus: true)
                    return (result == 0);
                }
            }
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


