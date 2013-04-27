package org.robolectric.shadows;

import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Display;
import org.jetbrains.annotations.NotNull;
import org.robolectric.Robolectric;
import org.robolectric.internal.HiddenApi;
import org.robolectric.internal.Implementation;
import org.robolectric.internal.Implements;
import org.robolectric.internal.RealObject;
import org.robolectric.res.Attribute;
import org.robolectric.res.DrawableNode;
import org.robolectric.res.Fs;
import org.robolectric.res.Plural;
import org.robolectric.res.ResName;
import org.robolectric.res.ResType;
import org.robolectric.res.ResourceIndex;
import org.robolectric.res.ResourceLoader;
import org.robolectric.res.Style;
import org.robolectric.res.TypedResource;
import org.robolectric.res.XmlFileLoader;
import org.robolectric.res.builder.DrawableBuilder;
import org.robolectric.res.builder.XmlFileBuilder;
import org.w3c.dom.Document;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

import static org.fest.reflect.core.Reflection.field;
import static org.robolectric.Robolectric.directlyOn;
import static org.robolectric.Robolectric.shadowOf;

/**
 * Shadow of {@code Resources} that simulates the loading of resources
 *
 * @see org.robolectric.RobolectricTestRunner#RobolectricTestRunner(Class)
 */
@SuppressWarnings({"UnusedDeclaration"})
@Implements(Resources.class)
public class ShadowResources {
    private static Resources system = null;

    private float density = 1.0f;
    private DisplayMetrics displayMetrics;
    private Display display;
    @RealObject Resources realResources;
    private ResourceLoader resourceLoader;
    private ResourceIndex resourceIndex;

    public static void setSystemResources(ResourceLoader systemResourceLoader) {
        AssetManager assetManager = Robolectric.newInstanceOf(AssetManager.class);
        ShadowAssetManager.bind(assetManager, null, systemResourceLoader);
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration config = new Configuration();
        system = ShadowResources.bind(new Resources(assetManager, metrics, config), systemResourceLoader);
    }

    static Resources bind(Resources resources, ResourceLoader resourceLoader) {
        ShadowResources shadowResources = shadowOf(resources);
        if (shadowResources.resourceLoader != null) throw new RuntimeException("ResourceLoader already set!");
        shadowResources.resourceLoader = resourceLoader;
        return resources;
    }

    @Implementation
    public void updateConfiguration(Configuration config, DisplayMetrics metrics) {
        shadowOf(realResources.getAssets()).setQualifiers(shadowOf(config).getQualifiers());
        directlyOn(realResources, Resources.class).updateConfiguration(config, metrics);
    }

//    todo: should implement this:
//    @Implementation
//    public void updateConfiguration(Configuration config, DisplayMetrics metrics, CompatibilityInfo compat) {
//    }

    @Implementation
    public int getIdentifier(String name, String defType, String defPackage) {
        ResourceIndex resourceIndex = resourceLoader.getResourceIndex();
        ResName resName = ResName.qualifyResName(name, defPackage, defType);
        return resourceIndex.getResourceId(resName);
    }

    @Implementation
    public String getResourceName(int resId) throws Resources.NotFoundException {
        return getResName(resId).getFullyQualifiedName();
    }

    @Implementation
    public String getResourcePackageName(int resId) throws Resources.NotFoundException {
        return getResName(resId).namespace;
    }

    @Implementation
    public String getResourceTypeName(int resId) throws Resources.NotFoundException {
        return getResName(resId).type;
    }

    @Implementation
    public String getResourceEntryName(int resId) throws Resources.NotFoundException {
        return getResName(resId).name;
    }

    private boolean isEmpty(String s) {
        return s == null || s.length() == 0;
    }

    private @NotNull ResName getResName(int id) {
        ResName resName = resourceLoader.getResourceIndex().getResName(id);
        if (resName == null) {
            throw new Resources.NotFoundException("Unable to find resource ID #0x" + Integer.toHexString(id));
        }
        return resName;
    }

    private String getQualifiers() {
        return shadowOf(realResources.getConfiguration()).getQualifiers();
    }

//    @Implementation
//    public ColorStateList getColorStateList(int id) {
//        String colorValue = resourceLoader.getColorValue(getResName(id), getQualifiers());
//        if (colorValue != null) {
//            return ColorStateList.valueOf(Color.parseColor(colorValue));
//        } else {
//            return new ColorStateList(new int[0][0], new int[0]);
//        }
//    }

    @Implementation
    public String getQuantityString(int id, int quantity, Object... formatArgs) throws Resources.NotFoundException {
        String raw = getQuantityString(id, quantity);
        return String.format(Locale.ENGLISH, raw, formatArgs);
    }

    @Implementation
    public String getQuantityString(int id, int quantity) throws Resources.NotFoundException {
        ResName resName = getResName(id);
        Plural plural = resourceLoader.getPlural(resName, quantity, getQualifiers());
        String string = plural.getString();
        ShadowAssetManager shadowAssetManager = shadowOf(realResources.getAssets());
        TypedResource typedResource = shadowAssetManager.resolve(new TypedResource(string, ResType.CHAR_SEQUENCE), getQualifiers(),
                new ResName(resName.namespace, "string", resName.name));
        return typedResource == null ? null : typedResource.asString();
    }

    @Implementation
    public InputStream openRawResource(int id) throws Resources.NotFoundException {
        return resourceLoader.getRawValue(getResName(id));
    }

    public void setDensity(float density) {
        this.density = density;
        if (displayMetrics != null) {
            displayMetrics.density = density;
        }
    }

    public void setDisplay(Display display) {
        this.display = display;
        displayMetrics = null;
    }

    @Implementation
    public DisplayMetrics getDisplayMetrics() {
        if (displayMetrics == null) {
            if (display == null) {
                display = Robolectric.newInstanceOf(Display.class);
            }

            displayMetrics = new DisplayMetrics();
            display.getMetrics(displayMetrics);
        }
        displayMetrics.density = this.density;
        return displayMetrics;
    }

    @Implementation
    public Drawable getDrawable(int drawableResourceId) throws Resources.NotFoundException {
        ResName resName = getResName(drawableResourceId);
        String qualifiers = getQualifiers();
        DrawableNode drawableNode = resourceLoader.getDrawableNode(resName, qualifiers);
        final DrawableBuilder drawableBuilder = new DrawableBuilder(getResourceLoader().getResourceIndex());
        return drawableBuilder.getDrawable(resName, realResources, drawableNode);
    }

    private static final String[] UNITS = {"dp", "dip", "pt", "px", "sp"};
    Float temporaryDimenConverter(String rawValue) {
        int end = rawValue.length();
        for (String unit : UNITS) {
            int index = rawValue.indexOf(unit);
            if (index >= 0 && end > index) {
                end = index;
            }
        }

        return Float.parseFloat(rawValue.substring(0, end));
    }

    @Implementation
    public XmlResourceParser getXml(int id) throws Resources.NotFoundException {
        ResName resName = getResName(id);
        Document document = resourceLoader.getXml(resName, getQualifiers());
        if (document == null) {
            throw new Resources.NotFoundException();
        }
        return new XmlFileBuilder().getXml(document, realResources, resName.namespace);
    }

    @HiddenApi @Implementation
    public XmlResourceParser loadXmlResourceParser(String file, int id, int assetCookie, String type) throws Resources.NotFoundException {
        Document document = new XmlFileLoader(null).parse(Fs.fileFromPath(file));
        if (document == null) {
            throw new Resources.NotFoundException();
        }
        return new XmlFileBuilder().getXml(document, realResources, "todo fixme!!!");
    }

    public ResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    private String notFound(int id) {
        return "couldn't find resource " + getResName(id).getFullyQualifiedName();
    }

    @Implements(Resources.Theme.class)
    public static class ShadowTheme {
        @RealObject Resources.Theme realTheme;
        protected Resources resources;
        private int styleResourceId;

        @Implementation
        public void applyStyle(int resid, boolean force) {
            this.styleResourceId = resid;
        }

        @Implementation
        public TypedArray obtainStyledAttributes(int[] attrs) {
            return obtainStyledAttributes(0, attrs);
        }

        @Implementation
        public TypedArray obtainStyledAttributes(int resid, int[] attrs) throws android.content.res.Resources.NotFoundException {
            return obtainStyledAttributes(null, attrs, 0, 0);
        }

        @Implementation
        public TypedArray obtainStyledAttributes(AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes) {
            Resources resources = getResources();

            ResourceLoader resourceLoader = shadowOf(resources).getResourceLoader();
            String qualifiers = shadowOf(resources).getQualifiers();

            // Load the style for the theme we represent. E.g. "@style/Theme.Robolectric"
            ResName themeStyleName = resourceLoader.getResourceIndex().getResName(styleResourceId);
            Style theme = resourceLoader.getStyle(themeStyleName, qualifiers);

            // Load the theme attribute for the default style attributes. E.g., attr/buttonStyle
            ResName defStyleName = resourceLoader.getResourceIndex().getResName(defStyleAttr);

            // Load the style for the default style attribute. E.g. "@style/Widget.Robolectric.Button";
            String defStyleNameValue = theme.getAttrValue(defStyleName.namespace + ":" + defStyleName.name);
            Style defStyle = resourceLoader.getStyle(new ResName(defStyleName.namespace, "style", defStyleName.name), qualifiers);

            for (int i = 0; i < attrs.length; i++) {
                int attr = attrs[i];
                ResName attrName = resourceLoader.getResourceIndex().getResName(attr);
                String attrValue;

                // if attr in attribute set, use its value
                // TODO look for attrvalue in attriburte set

                // else if attr in defStyle, use its value
//                if (defStyle != null) {
//                    attrValue = defStyle.getAttrValue(attrName);
//                }


            }

            if (set == null) {
                set = new RoboAttributeSet(new ArrayList<Attribute>(), resourceLoader, null);
            }

            return ShadowTypedArray.create(resources, set, attrs);
        }

        Resources getResources() {
            // ugh
            return field("this$0").ofType(Resources.class).in(realTheme).get();
        }
    }

    @Implementation
    public static Resources getSystem() {
        return system;
    }

    public static <T> T inject(Resources resources, T instance) {
        Object shadow = Robolectric.shadowOf_(instance);
        if (shadow instanceof UsesResources) {
            ((UsesResources) shadow).injectResources(resources);
        }
        return instance;
    }

    @Implements(Resources.NotFoundException.class)
    public static class ShadowNotFoundException {
        @RealObject Resources.NotFoundException realObject;

        private String message;

        public void __constructor__() {
        }

        public void __constructor__(String name) {
            this.message = name;
        }

        @Implementation
        public String toString() {
            return realObject.getClass().getName() + ": " + message;
        }
    }
}
