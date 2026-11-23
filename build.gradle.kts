plugins {
    java
}

group = "cn.mcfhm"
version = "0.4.1"

repositories {
    maven("https://repo.purpurmc.org/snapshots")
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

// 本地已构建的 Purpur 26.3 API，优先使用它（离线也能编译）
val localApi = file("G:/服务端/Purpur-26.3/purpur-api/build/libs/purpur-api-26.3.local-SNAPSHOT.jar")

dependencies {
    if (localApi.exists()) {
        compileOnly(files(localApi))
        // 服务端自带的库（Adventure / Guava / BungeeChat / JOML），离线编译用
        compileOnly(fileTree("G:/服务端/Purpur-26.3/libraries") {
            include("net/kyori/**/*.jar", "org/joml/**/*.jar", "com/google/guava/**/*.jar", "net/md-5/**/*.jar")
        })
    } else {
        compileOnly("org.purpurmc.purpur:purpur-api:26.3.build.2660-stable")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveFileName = "Furniture-${project.version}.jar"
}


