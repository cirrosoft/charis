def projectTools = new ProjectTools(this)
def instances = new Instances(this)
def route53 = new Route53(this)
def remote = new Remote(this)
def docker = new Docker(this)
def flyway = new Flyway(this)

node {
    def build = [
            appName : "charis-ballet",
            color : "grey",
            buildNumber : BUILD_NUMBER,
            instanceName : "charis-ballet", // dac
            instanceNameDb : "charis-ballet-db",
            instanceType : "t1.micro",
            instanceImage : "ami-1853ac65",
            instanceSecurityGroup : "ssh-http",
            instanceSecurityGroupDb : "ssh-mysql",
            instanceKeyPair : "deployment",
            commitHash : "",      // define after checkout (dac)
            commitHashFull : "",  // dac
            dockerName : "",      // dac
            dockerTag : "",       // dac
            awsSSHCredential : "deployment",
            dbCredential : "database",
            domainName : "charisballet.com."
    ]
    stage("\u265A Checkout") {
        build.color = projectTools.getBlueOrGreen()
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
        docker.buildCleanImageAsLatest(build.dockerName, "image.tar", [build.dockerTag])
    }

    def instanceIds
    stage("\u26E9 Infrastructure") {
        instanceIds = projectTools.ensureDockerInstance(
                build.awsSSHCredential,
                build.instanceName,
                build.instanceType,
                build.instanceImage,
                build.instanceSecurityGroup,
                build.instanceKeyPair,
                docker.installCommands.amazonLinux
        )
    }

    def instanceIdsDb
    def ipDb
    stage("\u26D3 Synchronize DB") {

        if (instances.instanceExists(build.instanceNameDb)) {
            // Production Database should already be present.
            //   If it is not there something else is seriously wrong
            //   When dealing with backup and migration make sure any
            //     manual db changes to an instance continue to match the
            //     name of the db instance in deployment.
            //     otherwise a new install will occur.
            instanceIdsDb = instances.getInstanceIds(build.instanceNameDb)
            ipDb = instances.getInstancePublicIP(instanceIdsDb[0])
        } else {
            // This case is only for first startup.
            instanceIdsDb = projectTools.ensureDockerInstance(
                    build.awsSSHCredential,
                    build.instanceNameDb,
                    build.instanceType,
                    build.instanceImage,
                    build.instanceSecurityGroupDb,
                    build.instanceKeyPair,
                    docker.installCommands.amazonLinux
            )
            ipDb = instances.getInstancePublicIP(instanceIdsDb[0])
            def dbDockerName = "mysql:5.7"
            withCredentials([usernamePassword(credentialsId: build.dbCredential, usernameVariable: 'DB_USERNAME', passwordVariable: 'DB_PASSWORD')]) {
                def dbDockerParams = "-p 3306:3306 --name db -e MYSQL_USER=${DB_USERNAME} -e MYSQL_PASSWORD=${DB_PASSWORD} -e MYSQL_ROOT_PASSWORD=${DB_PASSWORD} -e MYSQL_DATABASE=main -d"
                docker.deployImage(build.awsSSHCredential, ip, dbDockerName, dbDockerParams)
            }
        }
        // In this stage we migrate no matter what.
        // This takes changes from the resource/db/migrations directory and applies them to the deployed db.
        def url = "jdbc:mysql://${ipDb}:3306/main"
        flyway.migrateWithGradle(build.dbCredential, url)
    }

    stage("\u26A1 Deploy Application") {
        for (id in instanceIds) {
            def ip = instances.getInstancePublicIP(instanceIds[0])
            docker.deployImageFile(
                    build.awsSSHCredential,
                    ip,
                    "image.tar",
                    build.dockerName,
                    build,
                    build.dbCredential,
                    "jdbc:mysql://${ipDb}:3306/main"
            )
        }
        echo "Service Deployed"
    }

    def ip
    stage("\u267A Integration Test") {
        for (id in instanceIds) {
            ip = instances.getInstancePublicIP(id)
            remote.waitForUrlSuccess("http://${ip}/health")
        }
    }

    stage("\u21C6 Crossover") {
        def zoneId = route53.getHostedZoneId(build.domainName)
        route53.createRecord(zoneId, build.domainName, ip)
        route53.createRecord(zoneId, build.color+"."+build.domainName, ip)
    }


}

class ProjectTools implements Serializable {
    public def steps
    Instances instances
    Remote remote
    ProjectTools(inputStep) {
        steps = inputStep
        instances = new Instances(steps)
        remote = new Remote(steps)
    }

    ArrayList getSuccessfullBuilds() {
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
    String getBlueOrGreen() {
        def builds = getSuccessfullBuilds()
        def count = builds.size()
        if (count % 2 == 0) {
            return "blue"
        } else {
            return "green"
        }
    }
    String generateJavaPropertiesString(Map props) {
        def propsString = ""
        props.each{ k, v ->
            propsString += "-Dbuild."+k+"="+v+" "
        }
        return propsString
    }

    ArrayList<String> ensureDockerInstance(
            String sshCredentials,
            String instanceName,
            String instanceType,
            String instanceImage,
            String instanceSecurityGroup,
            String instanceKeyPair,
            ArrayList<String> dockerInstall) {
        def instanceIds
        if (instances.instanceExists(instanceName)) {
            instanceIds = instances.getInstanceIds(instanceName)
        } else {
            def instanceId = instances.createInstance(instanceName, instanceType, instanceImage, instanceSecurityGroup, instanceKeyPair)
            instanceIds = [instanceId]
            instances.waitForInstance(instanceId)
            def ip = instances.getInstancePublicIP(instanceId)
            remote.executeRemoteCommands(sshCredentials, ip, dockerInstall)
        }
        return instanceIds
    }
}


class Instances implements Serializable {
    public def steps
    Instances(inputStep) {steps = inputStep}

    String createInstance(String nameTag, String type, String ami, String securityGroup, String keyPairName) {
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

    String[] getInstanceIds(nameTag) {
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

    boolean instanceExists(String nameTag) {
        def instances = this.getInstanceIds(nameTag)
        if (instances) {
            return true
        } else {
            return false
        }
    }

    void deleteInstance(String id) {
        steps.sh(script: """aws ec2 terminate-instances --instance-ids ${id}""")
    }

    void deleteInstances(String nameTag) {
        nameTag = nameTag.replaceAll(" ", "-")
        def instances = this.getInstanceIds(nameTag)
        for (id in instances) {
            this.deleteInstance(id)
        }
    }

    void waitForInstance(String id) {
        steps.echo "Waiting for instance to start..."
        steps.sh(script: """aws ec2 wait instance-running --instance-ids ${id}""")
        steps.echo "Waiting for instance to become available to the network..."
        steps.sh(script: """aws ec2 wait instance-status-ok --instance-ids ${id}""")
    }

    String getInstancePublicIP(String id) {
        return steps.sh(script: """aws ec2 describe-instances --instance-ids ${id} | grep PublicIpAddress | awk -F ":" '{print \$2}' | sed 's/[",]//g'""", returnStdout: true).trim()
    }

    String getInstancePrivateIP(String id) {
        return steps.sh(script: """aws ec2 describe-instances --instance-ids ${id} | grep PrivateIpAddress  | head -1 | awk -F ":" '{print \$2}' | sed 's/[",]//g'""", returnStdout: true).trim()
    }

}

class Route53 implements Serializable {
    public def steps
    Route53(inputStep) {steps = inputStep}

    String getHostedZoneId(String domainName) {
        steps.sh(script: """aws route53 list-hosted-zones | jq '.HostedZones[] | select(.Name=="${domainName}")' | tee zones.out""", returnStdout: true)
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
    String createRecord(String zoneId, String domainName, String ip) {
        def record = """
        {
            "Comment": "A new record set for the zone.",
            "Changes": [
                {
                    "Action": "UPSERT",
                    "ResourceRecordSet": {
                    "Name": "${domainName}",
                    "Type": "A",
                    "TTL": 300,
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
        steps.echo record
        steps.sh """touch dns-record.json"""
        steps.writeFile file: "dns-record.json", text: record
        steps.sleep 1
        steps.sh(script: """aws route53 change-resource-record-sets --hosted-zone-id ${zoneId} --change-batch file://dns-record.json | tee change.out""", returnStdout: true)
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

class Remote implements Serializable {
    public def steps
    Remote(inputStep) {steps = inputStep}

    String executeRemoteCommands(String credentialId, String address, ArrayList commands) {
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

    void scp(String credentialId, String address, String fromPath, String toPath) {
        address = address.trim()
        steps.withCredentials([steps.sshUserPrivateKey(credentialsId: credentialId, keyFileVariable: 'SSH_KEYFILE', passphraseVariable: 'SSH_PASSWORD', usernameVariable: 'SSH_USERNAME')]) {
            steps.sh """
           scp -i ${steps.SSH_KEYFILE} -B ${fromPath} ${steps.SSH_USERNAME}@${address}:${toPath}
           """
        }
    }

    void waitForUrlSuccess(String url) {
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

class Docker implements Serializable {
    public def steps
    ProjectTools projectTools
    Remote remote
    Docker(inputStep) {
        steps = inputStep
        projectTools = new ProjectTools(steps)
        remote = new Remote(steps)
    }

    public def installCommands = [
            amazonLinux: [
                    "uname -a",
                    "sudo yum update -y",
                    "sudo yum install docker -y",
                    "sudo service docker start",
                    "sudo usermod -a -G docker ec2-user"
            ]
    ]
    void buildCleanImageAsLatest(String imageName, String filename, ArrayList additionalImageTags = []) {
        def tagsString = ""
        for (tag in additionalImageTags) {
            tagsString += "-t " + imageName + ":" + tag + " "
        }
        steps.sh(script: "docker build ${tagsString} -t ${imageName}:latest .")
        steps.sh(script: "docker image save ${imageName}:latest > ${filename}")
        steps.sh(script: "docker rmi -f `docker images -a -q --filter=reference=\"${imageName}:*\"`")
    }

    void deployImageFile(
            String sshCred,
            String address,
            String imageFileString,
            String imageName,
            LinkedHashMap buildMap,
            String dbCredential = null,
            String dbAddress = null
    ) {
        remote.executeRemoteCommands(sshCred, address, ["rm -rf ${imageFileString}"]) // remove previous tar
        remote.scp(sshCred, address, imageFileString, imageFileString) // deploy new tar
        // Stop and cleanup old containers
        def runningContainers = remote.executeRemoteCommands(sshCred, address, ["docker ps -a -q --filter=\"ancestor=${imageName}:latest\""])
        runningContainers?.trim()?.eachLine {
            remote.executeRemoteCommands(sshCred, address, ["docker stop ${it}"])
            remote.executeRemoteCommands(sshCred, address, ["docker rm ${it}"])
        }
        // Deploy new container
        def javaParams = projectTools.generateJavaPropertiesString(buildMap)
        if (dbCredential && dbAddress) {
            steps.withCredentials([steps.usernamePassword(credentialsId: dbCredential, usernameVariable: 'DB_USERNAME', passwordVariable: 'DB_PASSWORD')]) {
                javaParams += "-Dspring.datasource.url=${dbAddress} -Dspring.datasource.username=${steps.DB_USERNAME} -Dspring.datasource.password=${steps.DB_PASSWORD}"
            }
        }
        def commands = [
                "docker image load -i ${imageFileString}",
                "sudo docker run -e JAVA_OPTS=\\\"${javaParams}\\\" -d -p \"80:8080\" ${imageName}:latest"
        ]
        remote.executeRemoteCommands(sshCred, address, commands)
    }

    void deployImage(
            String sshCred,
            String address,
            String imageNameAndTag,
            String params
    ) {
        // Stop and cleanup old containers
        def runningContainers = remote.executeRemoteCommands(sshCred, address, ["docker ps -a -q --filter=\"ancestor=${imageNameAndTag}\""])
        runningContainers?.trim()?.eachLine {
            remote.executeRemoteCommands(sshCred, address, ["docker stop ${it}"])
            remote.executeRemoteCommands(sshCred, address, ["docker rm ${it}"])
        }
        // Run
        def commands = [
                "sudo docker run ${params} ${imageNameAndTag}"
        ]
        remote.executeRemoteCommands(sshCred, address, commands)
    }

}

class Flyway implements Serializable {
    public def steps
    Flyway(inputStep) {steps = inputStep}
    void migrateWithGradle(String dbCredential, url) {
        steps.withCredentials([steps.usernamePassword(credentialsId: dbCredential, usernameVariable: 'DB_USERNAME', passwordVariable: 'DB_PASSWORD')]) {
            steps.timeout(1) {
                steps.waitUntil {
                    steps.script {
                        steps.echo "Waiting for response from ${url}"
                        def result = steps.sh(script: """./gradlew -Dflyway.user=${steps.DB_USERNAME} -Dflyway.password=${steps.DB_PASSWORD} -Dflyway.url=${url}  flywayMigrate""", returnStatus: true)
                        return (result == 0);
                    }
                }
            }

        }
    }
}


