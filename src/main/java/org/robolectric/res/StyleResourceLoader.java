package org.robolectric.res;

import javax.xml.xpath.XPathExpressionException;

public class StyleResourceLoader extends XpathResourceXmlLoader {
    private final ResBundle<StyleData> styleData;

    public StyleResourceLoader(ResBundle<StyleData> styleData) {
        super("/resources/style");
        this.styleData = styleData;
    }

    @Override
    protected void processNode(String name, XmlNode xmlNode, XmlContext xmlContext) throws XPathExpressionException {
        String styleName = xmlNode.getAttrValue("name").replace('.', '_');
        String styleParent = xmlNode.getAttrValue("parent").replace('.', '_');

        StyleData style = new StyleData(styleName, styleParent);

        for (XmlNode item : xmlNode.selectElements("item")) {
            String attrName = item.getAttrValue("name");
            String value = item.getTextContent();

            style.add(attrName, value);
        }

        styleData.put("style", styleName, style, xmlContext);
    }
}
