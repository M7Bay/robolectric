package org.robolectric.res;

import org.robolectric.util.I18nException;

public class PackageResourceLoader extends XResourceLoader {
    ResourcePath resourcePath;
    ResourceIndex resourceIndex;

    public PackageResourceLoader(ResourcePath resourcePath) {
        this(resourcePath, new ResourceExtractor(resourcePath));
    }

    public PackageResourceLoader(ResourcePath resourcePath, ResourceIndex resourceIndex) {
        super(resourceIndex);
        this.resourcePath = resourcePath;
    }

    void doInitialize() {
        try {
            loadEverything();
        } catch (I18nException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void loadEverything() throws Exception {
        System.out.println("DEBUG: Loading resources for " + resourcePath.getPackageName() + " from " + resourcePath.resourceBase + "...");

        DocumentLoader documentLoader = new DocumentLoader(resourcePath);
        documentLoader.load("values",
                new ValueResourceLoader(data, "/resources/bool", "bool", ResType.BOOLEAN),
                new ValueResourceLoader(data, "/resources/color", "color", ResType.COLOR),
                new ValueResourceLoader(data, "/resources/dimen", "dimen", ResType.DIMEN),
                new ValueResourceLoader(data, "/resources/integer", "integer", ResType.INTEGER),
                new ValueResourceLoader(data, "/resources/integer-array", "array", ResType.INTEGER_ARRAY),
                new PluralResourceLoader(pluralsData),
                new ValueResourceLoader(data, "/resources/string", "string", ResType.CHAR_SEQUENCE),
                new ValueResourceLoader(data, "/resources/string-array", "array", ResType.CHAR_SEQUENCE_ARRAY),
                attrResourceLoader
        );

        documentLoader.load("layout", new LayoutLoader(layoutData));
        documentLoader.load("menu", new MenuLoader(menuData));
        DrawableResourceLoader drawableResourceLoader = new DrawableResourceLoader(drawableData);
        drawableResourceLoader.findNinePatchResources(resourcePath);
        documentLoader.load("drawable", drawableResourceLoader);
        documentLoader.load("xml", new PreferenceLoader(preferenceData));
        documentLoader.load("xml", new XmlFileLoader(xmlDocuments));
        new RawResourceLoader(resourcePath).loadTo(rawResources);

        loadOtherResources(resourcePath);
    }

    protected void loadOtherResources(ResourcePath resourcePath) {
    }

    @Override
    public String toString() {
        return "PackageResourceLoader{" +
                "resourcePath=" + resourcePath +
                '}';
    }
}
