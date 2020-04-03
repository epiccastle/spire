# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

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

[Unreleased]: https://github.com/epiccastle/spire/compare/v0.1.0-alpha.2...HEAD
[0.1.0-alpha.2]: https://github.com/epiccastle/spire/compare/v0.1.0-alpha1...v0.1.0-alpha.2
[0.1.0-alpha.1]: https://github.com/epiccastle/spire/tree/v0.1.0-alpha.1
