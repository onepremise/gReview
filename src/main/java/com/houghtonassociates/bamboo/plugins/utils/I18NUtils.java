/*
 * Houghton Associates Framework
 * http://www.houghtonassociates.com
 * 
 * Copyright 2012 Houghton Associates, Inc.
 */
package com.houghtonassociates.bamboo.plugins.utils;

import com.opensymphony.xwork.TextProvider;
import com.opensymphony.xwork.util.LocalizedTextUtil;

/**
 * TextProvider update class for adding plugin resource bundle to
 * existing babmoo i18n bundles.
 * 
 * @author Jason Huntley
 * 
 */
public class I18NUtils {

    public static String getKeyValue(TextProvider textProvider, String key) {
        return textProvider.getText(key);
    }

    public static synchronized TextProvider
                    updateTextProvider(TextProvider textProvider, String testKey) {
        if (getKeyValue(textProvider, testKey) == null) {
            ClassLoader loader = I18NUtils.class.getClassLoader();

            LocalizedTextUtil.setDelegatedClassLoader(loader);
            LocalizedTextUtil
                .addDefaultResourceBundle("com.houghtonassociates.bamboo.plugins.i18n.i18n");

            // If still null, reset so the lookup can occur with new delegated
            // class loader.
            if (getKeyValue(textProvider, testKey) == null)
                LocalizedTextUtil.reset();
        }

        return textProvider;
    }
}
