/**
 *  Copyright (C) 2009-2012 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.enforcer

import scala.tools.nsc.{ Global, Phase }
import scala.tools.nsc.plugins.{ Plugin, PluginComponent }

class EnforcerPlugin(val global: Global) extends Plugin {
  import global._

  val name = "akka-enforcer"
  val description = "Enforces coding standards"
  val components = List[PluginComponent](EnforcerComponent)

  private object EnforcerComponent extends PluginComponent {
    import global._

    val global = EnforcerPlugin.this.global

    override val runsAfter = List("typer")

    val phaseName = "enforcer"

    override def newPhase(prev: Phase): StdPhase = new StdPhase(prev) {
      override def apply(unit: global.CompilationUnit): Unit = {
        new EnforcerTraverser(unit).traverse(unit.body)
      }
    }

    val scalaSeqValue = findMemberFromRoot(newTermName("scala.Seq"))
    val scalaSeqType = findMemberFromRoot(newTypeName("scala.Seq"))

    class EnforcerTraverser(unit: CompilationUnit) extends Traverser {
      def originalOrSelf(typeTree: TypeTree) = Option(typeTree.original).getOrElse(typeTree)

      override def traverse(tree: Tree): Unit = tree match {
        case tpt: TypeTree if originalOrSelf(tpt).symbol == scalaSeqType ⇒
          unit.warning(tpt.pos, "Use of type alias scala.Seq prohibited, use collection(.(immutable|mutable)).Seq instead")
        case sel: SymTree if sel.symbol == scalaSeqValue ⇒
          // TODO This warning never fires, because of this shortcut in Typers for Seq, Nil, and List
          // https://github.com/scala/scala/blob/v2.10.0-RC1/src/compiler/scala/tools/nsc/typechecker/Typers.scala#L570
          unit.warning(sel.pos, "Use of value scala.Seq prohibited, use collection(.(immutable|mutable)).Seq instead")
        case _ ⇒
          super.traverse(tree)
      }
    }
  }
}
