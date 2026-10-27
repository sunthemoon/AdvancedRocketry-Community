package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;

/** Installs retained state in constructor-free adapter fixtures without duplicating private field layouts. */
public final class LoaderFixtureData {
    public static void install(FluidFuelLoaderBlockEntity device, LoaderRoot root, LoaderRoot.Status status) {
        var data = new LoaderRetainedRoot();
        if (root != null) { data.write(root); }
        data.status = status;
        UnstartedServer.set(FluidFuelLoaderBlockEntity.class, device, "data", data);
    }
    private LoaderFixtureData() { }
}
