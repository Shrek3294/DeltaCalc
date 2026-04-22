plugins {
    id("fabric-loom") version "1.10-SNAPSHOT"
    id("maven-publish")
    kotlin("jvm") version "2.2.0"
}

version = property("mod_version")!!
group = property("maven_group")!!

base {
    archivesName.set(property("archives_base_name").toString())
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.impactdev.net/repository/development/")
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
    maven("https://maven.architectury.dev/")
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/")
}

sourceSets {
    main {
        java {
            exclude("com/deltacalc/**")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    modImplementation("com.cobblemon:fabric:${property("cobblemon_version")}")

    modCompileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")
    modCompileOnly("me.shedaniel.cloth:cloth-config-fabric:${property("cloth_config_version")}") {
        exclude(group = "net.fabricmc.fabric-api")
    }

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    archiveFileName.set("deltacalc-dev.jar")
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveFileName.set("deltacalc.jar")
}

val prismJar by tasks.registering(Copy::class) {
    dependsOn(tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar"))
    from(tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar").flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("libs"))
    rename { "deltacalc-prism.jar" }
}

tasks.named<org.gradle.jvm.tasks.Jar>("sourcesJar") {
    archiveFileName.set("deltacalc-sources.jar")
}

val purgePrototypeArtifacts by tasks.registering(Delete::class) {
    delete(fileTree(layout.buildDirectory.dir("libs")) {
        include("deltacalc-*.jar")
        include("cobblemonextendedbattleui-*.jar")
    })
}

tasks.named("build") {
    dependsOn(purgePrototypeArtifacts)
    dependsOn(prismJar)
}

tasks.named("assemble") {
    dependsOn(purgePrototypeArtifacts)
    dependsOn(prismJar)
}

tasks.named("clean") {
    doFirst {
        delete(fileTree(layout.buildDirectory.dir("libs")) {
            include("deltacalc-*.jar")
            include("cobblemonextendedbattleui-*.jar")
        })
    }
}

tasks.test {
    useJUnitPlatform()
}

loom {
    accessWidenerPath.set(file("src/main/resources/cobblemonextendedbattleui.accesswidener"))
    mixin {
        defaultRefmapName.set("cobblemonextendedbattleui-refmap.json")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
