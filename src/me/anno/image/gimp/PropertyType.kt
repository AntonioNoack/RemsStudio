package me.anno.image.gimp

enum class PropertyType {
    END,
    COLORMAP,
    ACTIVE_LAYER,
    ACTIVE_CHANNEL,
    SELECTION,
    FLOATING_SELECTION,
    OPACITY,
    MODE,
    VISIBLE,
    LINKED,
    LOCK_ALPHA,
    APPLY_MASK,
    EDIT_MASK,
    SHOW_MASK,
    SHOW_MASKED,
    OFFSETS,
    COLOR,
    COMPRESSION,
    GUIDES,
    RESOLUTION,
    TATTOO,
    PARASITES,
    UNIT,
    PATHS,
    USER_UNIT,
    VECTORS,
    TEXT_LAYER_FLAGS,
    OLD_SAMPLE_POINTS,
    LOCK_CONTENT,
    GROUP_ITEM,
    ITEM_PATH,
    GROUP_ITEM_FLAGS,
    LOCK_POSITION,
    FLOAT_OPACITY,
    COLOR_TAG,
    COMPOSITE_MODE,
    COMPOSITE_SPACE,
    BLEND_SPACE,
    FLOAT_COLOR,
    SAMPLE_POINTS;

    companion object {
        val values2 = values()
    }
}