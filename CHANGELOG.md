# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

## [0.1.0-alpha.6] - 2020-04-26

### Added
- implement external code loading via `require` or `load-file` - #25

### Fixed
- Fix hanging or breaking file upload when using multibyte unicode `:content` - #38
- Make `upload` file `:src` path relative to executing script's folder - #39

## [0.1.0-alpha.5] - 2020-04-19

### Fixed
- NumberFormatException calling get-fact on Fedora 31 - #36

## [0.1.0-alpha.4] - 2020-04-17

### Added
- :event printing output module
- mkdir module
- attrs module
- rm module
- source file relative directory for selmer template file paths - #30

### Fixed
- uploading with :context and progress bar causes Divide by zero - #29
- System/getenv reflection fix

### Changed
- selmer file path now relative to executing clj file

## [0.1.0-alpha.3] - 2020-04-03

### Fixed
- connection config bug - #22

### Removed
- Removed some excess reflection

### Added
- sudo support - #12
- ssh connection debug with --debug-ssh parameter - #23
- pluggable output controllers
- quiet output (-o :quiet)
- support for sash, yash, zsh as default shell
- sysctl :absent support
- added `failed?` test
- added `debug` output macro - #21

### Changed
- replace clojure dynamic vars with sci dynamic vars

## [0.1.0-alpha.2] - 2020-03-08
### Fixed
- Fact gathering for when default shell is fish - #9
- General refactor of fact gathering - #9
- csh returns exit code of last subshell bug on path gathering fixed
- off by one bug on reporting line numbers when shebang is in use
- service module on FreeBSD - #19
- handle ctrl-c keypress when entering password or passphrase - #11

### Added
- edamame.core namespace - #15
- clojure.edn namespace - #15
- clojure.java.shell namespace - #16
- fix broken reflection for using `assert`
- stat module for bsd and linux
- stat module result test predicates
- current shell user and group information in facts
- some documentation
- pkg :install for FreeBSD

## [0.1.0-alpha.1] - 2020-02-26
Initial release

[Unreleased]: https://github.com/epiccastle/spire/compare/v0.1.0-alpha.6...HEAD
[0.1.0-alpha.6]: https://github.com/epiccastle/spire/compare/v0.1.0-alpha.5...v0.1.0-alpha.6
[0.1.0-alpha.5]: https://github.com/epiccastle/spire/compare/v0.1.0-alpha.4...v0.1.0-alpha.5
[0.1.0-alpha.4]: https://github.com/epiccastle/spire/compare/v0.1.0-alpha.3...v0.1.0-alpha.4
[0.1.0-alpha.3]: https://github.com/epiccastle/spire/compare/v0.1.0-alpha.2...v0.1.0-alpha.3
[0.1.0-alpha.2]: https://github.com/epiccastle/spire/compare/v0.1.0-alpha.1...v0.1.0-alpha.2
[0.1.0-alpha.1]: https://github.com/epiccastle/spire/tree/v0.1.0-alpha.1
