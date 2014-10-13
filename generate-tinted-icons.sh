#!/usr/bin/env bash
set -e

DARK_ICONS=(
ic_action_back_dark.png
ic_action_overflow_dark.png
ic_action_refresh_dark.png
ic_action_search_dark.png
ic_drawer_dark.png
)

LIGHT_ICONS=(
ic_action_back_light.png
ic_action_overflow_light.png
ic_action_refresh_light.png
ic_action_search_light.png
ic_drawer_light.png
)

DARK_DEFAULT_COLOR=cbcbcb
LIGHT_DEFAULT_COLOR=777777

BASEDIR=$(dirname $0)

function showHelp {
    echo "Usage: $0 (dark|light) tintColor"
    echo "Example: $0 light 0000ff"
    exit 1

}

if [[ $# -ne 2 ]]; then
    showHelp
fi

theme=`echo $1 | tr '[:upper:]' '[:lower:]'`
tintColor=`echo $2 | tr '[:upper:]' '[:lower:]'`

if [[ $theme = light ]]; then
    icons=("${LIGHT_ICONS[@]}")
    defaultColor=$LIGHT_DEFAULT_COLOR
elif [[ $theme = dark ]]; then
    icons=("${DARK_ICONS[@]}")  
    defaultColor=$DARK_DEFAULT_COLOR
else
    showHelp
fi

if [[ ${#tintColor} -ne 6 ]]; then
    showHelp
fi

if [[ $tintColor = $defaultColor ]]; then
    echo "Requested color $tintColor is same as default for theme. Exiting."
    exit
fi

for drawable in `ls -d $BASEDIR/app/src/main/res/drawable*`; do
    for file in  "${icons[@]}"; do
        filePath=$drawable/$file
        if [[ -s "$filePath" ]]; then
            echo Tinting $filePath
            convert $filePath -fill "#$tintColor" +opaque "" $filePath
            pngcrush -q -rem allb -brute -reduce -ow $filePath
        fi
    done
done
