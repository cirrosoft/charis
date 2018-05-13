@Library("jenkins-pipeline") _
import org.cirrosoft.pipeline.*

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




