# PrintGameObjectsToJson

Loads resources from an Infinity Engine game and writes their parsed fields to a JSON file.

## Usage

```bash
java -jar NearInfinity.jar --run-tool PrintGameObjectsToJson input.json
```

The input filename is passed to the tool. Relative paths in the JSON document are resolved relative to the directory
containing the input JSON file.

## Input format

```json
{
  "game": "C:/Games/BaldursGate2/chitin.key",
  "weidu": "C:/Games/BaldursGate2/weidu.exe",
  "resources": [
    "^sw.*\\.itm$",
    "^spwi.*\\.spl$"
  ],
  "output": "build/game-objects"
}
```

`game` must point to the game's `chitin.key` file. Near Infinity loads the game resources, override files, and dialog
table while processing the request.

`weidu` must point to a WeiDU executable. The path is used to initialize Near Infinity's WeiDU option before the
command-line tool loads GUI-backed resource definitions. This also avoids depending on the user's saved Near Infinity
preferences. Relative paths are resolved relative to the input JSON file.

`resources` is a non-empty array of regular expressions. Expressions are matched against complete resource filenames,
including their extensions, without regard to case. Resources in the override folder and resources in the game's BIFF
archives are both considered. If an override resource has the same name as an archived resource, the override version is
used.

`output` must specify an existing empty folder. One JSON file is created for each matching resource, using the resource
filename plus `.json`; for example, `sw1h01.itm` is written to `sw1h01.itm.json`. The tool does not create the folder
and fails if it contains any files or subfolders.

Each matching resource is written as a top-level property keyed by its filename. Fields are represented using the
parsed Near Infinity structure and field names used by the resource viewer. Nested structures become JSON objects,
repeated field names become arrays, resource references include their file extensions, and lookup-table values include
both their numeric value and description. Bitmasks include the hexadecimal value stored in the file and an array of the
set bits with their bit number, description, and value.

## Example

The [example input](examples/PrintGameObjectsToJson.json) extracts item and spell resources. Run
[print-game-objects.bat](examples/print-game-objects.bat) from this directory after changing the game path in the
sample JSON file and setting its WeiDU executable path.
