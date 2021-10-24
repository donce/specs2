package org.specs2.runner

import java.io.File

import org.specs2.Spec
import org.specs2.control.Operation
import org.specs2.io.FileSystem.{filePaths, filterWithPattern, globToPattern}
import org.specs2.io._
import org.specs2.matcher.Matcher
import org.specs2.matcher.OperationMatchers.beOk
import org.specs2.matcher.MatchersImplicits._

class SpecificationsFinderSpec extends Spec { def is = s2"""
  It is possible to find specifications in the local test directory           $e1
  It is possible to find specifications in an absolute test directory         $e2
  It is possible to find specifications in a specific drive                   $e3
  If a specification can not be instantiated it is dropped with a warning     $e4
"""

  val base = new File(".").getAbsolutePath

  def e1 =
    filePaths(DirectoryPath.unsafe(base) / "src" / "test" / "scala", "**/*.scala", verbose = false) must findFiles

  def e2 =
    filePaths(DirectoryPath.unsafe(new File(base+"/src/test/scala")), "**/*.scala", verbose = false) must findFiles

  def e3 =
    filterWithPattern(globToPattern("**/*.scala"))(FilePath.unsafe(new File("T:/"+new File("src/test/scala/org/specs2/runner/SpecificationsFinderSpec.scala").getAbsolutePath))) must
      beTrue

  def e4 = {
    val filter = (s: String) =>
      s.contains("SourceFileSpec") || // SourceFileSpec cannot be instantiated
      s.contains("SpecificationsFinderSpec")

    SpecificationsFinder.findSpecifications(
      basePath = DirectoryPath.unsafe(base) / "src" / "test" / "scala",
      filter = filter
    ).runOption must beSome((l: List[?]) => l must haveSize(1))
  }

  def findFiles: Matcher[Operation[List[FilePath]]] = (operation: Operation[List[FilePath]]) =>
    operation must beOk((_: List[FilePath]) must not(beEmpty))
}
