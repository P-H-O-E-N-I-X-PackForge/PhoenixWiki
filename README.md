<h1 align="center">
  <div style="
    display: inline-block;
    width: 32px;
    height: 32px;
    background-image: url('https://raw.githubusercontent.com/Omicron-Industries/PhoenixWiki/master/src/main/resources/assets/phoenix_wiki/textures/items/phoenix_feather.png');
    background-position: 0 0;
    background-repeat: no-repeat;
    image-rendering: pixelated;
    transform: scale(8.75);
    transform-origin: center top;
    margin-bottom: 240px;
  "></div>
</h1>

<p align="center">
  <strong>The theme of your own life can be as fleeting as a rose. But it can also be just as pretty.<br>
    Learning about yourself causes you to spark and flume.</strong>
</p>

<p align="center">
  <a href="https://www.curseforge.com/minecraft/mc-mods/phoenixwiki">
    <img alt="CurseForge" height="50" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/available/curseforge_vector.svg"></a>
  <a href="https://discord.gg/4jch9Rs2Cq">
    <img alt="Discord" height="50" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/social/discord-singular_vector.svg"></a>
  <a href="https://ko-fi.com/phoenixvine">
    <img alt="Ko-fi" height="50" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/donate/kofi-singular_vector.svg"></a>
</p>

# What is Phoenix Wiki?
Wiki is a small mod designed to be used within the PhoenixSuite as a library mod.
It handles themeing, the Markdown parsing, and the ingame wiki.  It is meant to be JarinJar included in your mod.

Contributions are highly welcome!

## Wiki link
We have a small in progress wiki for all PhoenixSuite mods, if it is missing any important info or you would like to help,
feel free to ping me on discord by the username of Phoenixvine.

[Wiki](https://omicron-industries.github.io/PhoenixSuite/wiki/)

# Getting started as a dev
If you are a dev wanting to use Wiki in your mod, you will need to:
1. Depend on the mod using either cursemaven or the repsy repository.
```gradle
    // Add the following to your mavens section
    repositories {
        maven {
            url "https://cursemaven.com/"
            content {
                includeGroup "curse.maven"
            }
        }
        
        maven {
            name = "Repsy"
            url = uri("https://repo.repsy.io/mvn/user75142941/phoenixsuite")
        }
    }
    
    dependencies {
        modImplementation("net.phoenixvine.wiki:phoenix_wiki:0.2.8")
    }
```


