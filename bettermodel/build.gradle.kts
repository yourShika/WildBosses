val paperVersion: String by project
val betterModelVersion: String by project

dependencies {
    // compileOnly on :core → one-directional (core does NOT compile-depend on this module),
    // so no circular dependency. Provides the ModelAdapter interface to implement.
    compileOnly(project(":core"))
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    // The BetterModel developer API. Provided at runtime by the installed BetterModel plugin.
    // bettermodel-api = core API (BetterModel entry, ModelRenderer, EntityTracker);
    // bettermodel-bukkit-api = Bukkit platform adapters (BukkitAdapter).
    compileOnly("io.github.toxicity188:bettermodel-api:$betterModelVersion")
    compileOnly("io.github.toxicity188:bettermodel-bukkit-api:$betterModelVersion")
}
