/* SPDX-FileCopyrightText: © 2021 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.expr

import parsley.Parsley
import parsley.combinator.choice

/** This object is used to construct precedence parsers from either a `Prec` or many `Ops[A, A]`.
  *
  * Contained within this object are three different shapes of `apply` functions: they allows for
  * the construction of a precedence parser from either: a collection of levels of operators and atoms,
  * in either weak-to-strong or strong-to-weak orderings; or a heterogeneous precedence table [[Prec `Prec`]].
  *
  * @since 2.2.0
  * @group Precedence
  */
object precedence {
    /** This combinator builds an expression parser given a collection of homogeneous atoms and operators.
      *
      * An expression parser will be formed by parsing `atom0` and the remaining `atoms` at
      * the base of the table. Then `lvlTightest` will be the level containing the tightest
      * operators. The remaining `lvls` will be from tightest-to-weakest binding. All levels
      * must consume and produce the same type.
      *
      * @example {{{
      * scala> import parsley.Parsley, parsley.character.{char, digit}
      * scala> import parsley.expr.{Ops, InfixL, precedence}
      * scala> val expr = precedence(digit.map(_.asDigit))
      *                             (Ops(InfixL)(char('*') #> (_ * _)),
      *                              Ops(InfixL)(char('+') #> (_ + _), char('-') #> (_ - _)))
      * scala> expr.parse("1+8*7+4")
      * val res0 = Success(61)
      * }}}
      *
      * @tparam A          the type of the expression parsers generated by this combinator.
      * @param atom0       the first atom at the base of the table.
      * @param atoms       the remaining atoms at the base of the table.
      * @param lvlTightest the tightest binding operators.
      * @param lvls        the remaining levels of operators, ordered '''tightest-to-weakest'''.
      * @return            an expression parser for the described precedence table.
      * @since 3.0.0
      */
    def apply[A](atom0: Parsley[A], atoms: Parsley[A]*)(lvlTightest: Ops[A, A], lvls: Ops[A, A]*): Parsley[A] = {
        apply(lvls.foldLeft[Prec[A]](new Level(Atoms(atom0, atoms: _*), lvlTightest))(new Level(_, _)))
    }

    /** This combinator builds an expression parser given a collection of homogeneous atoms and operators.
      *
      * An expression parser will be formed by parsing `atom0` and the remaining `atoms` at
      * the base of the table. Then `lvlWeakest` will be the level containing the weakest
      * operators at the outermost layer of the table. The remaining `lvls` will be from
      * tightest-to-weakest binding. All levels must consume and produce the same type.
      *
      * @example {{{
      * scala> import parsley.Parsley, parsley.character.{char, digit}
      * scala> import parsley.expr.{Ops, InfixL, precedence}
      * scala> val expr = precedence[Int](Ops(InfixL)(char('+') #> (_ + _), char('-') #> (_ - _))),
      *                                   Ops(InfixL)(char('*') #> (_ * _))
      *                                  (digit.map(_.asDigit)))
      * scala> expr.parse("1+8*7+4")
      * val res0 = Success(61)
      * }}}
      *
      * Note that the type ascription on `precedence` is needed for this example, to avoid specifying
      * the argument types of the operators in the table: this wouldn't be required with the tightest-to-weakest
      * variant, as the inference is better on the atoms.
      *
      * @tparam A         the type of the expression parsers generated by this combinator.
      * @param lvlWeakest the weakest binding operators.
      * @param lvls       the remaining levels of operators, ordered '''weakest-to-tightest'''.
      * @param atom0      the first atom at the base of the table.
      * @param atoms      the remaining atoms at the base of the table.
      * @return           an expression parser for the described precedence table.
      * @since 4.0.0
      */
    def apply[A](lvlWeakest: Ops[A, A], lvls: Ops[A, A]*)(atom0: Parsley[A], atoms: Parsley[A]*): Parsley[A] = {
        val (lvlTightest +: lvls_) = (lvlWeakest +: lvls).reverse
        apply(atom0, atoms: _*)(lvlTightest, lvls_ : _*)
    }

    /** This combinator builds an expression parser given a heterogeneous precedence table.
      *
      * An expression parser will be formed by collapsing the given precedence table
      * layer-by-layer. Since this table is heterogeneous, each level of the table
      * produces a difference type, which is then consumed by the next level above.
      *
      * @example This is overkill for this particular example, as each layer has the same type:
      * it would be better to use one of the other forms of `precedence` for simplicity. This
      * is best used in conjunction with `SOps` or `GOps`; or a mix of `SOps`, `GOps`, and `Ops`.
      * {{{
      * scala> import parsley.Parsley, parsley.character.{char, digit}
      * scala> import parsley.expr.{Atoms, Ops, InfixL, precedence}
      * scala> val expr = precedence(Atoms(digit.map(_.asDigit)) :+
      *                              Ops[Int](InfixL)(char('*') #> (_ * _)) :+
      *                              Ops[Int](InfixL)(char('+') #> (_ + _), char('-') #> (_ - _)))
      * scala> expr.parse("1+8*7+4")
      * val res0 = Success(61)
      * }}}
      *
      * @tparam      A the type of the expression parsers generated by this combinator.
      * @param table the description of the heterogeneous table, where each level can vary in output and input types.
      * @return      an expression parser for the described precedence table.
      * @see         [[Prec `Prec`]] and its subtypes for a description of how the types work.
      * @since 4.0.0
      */
    def apply[A](table: Prec[A]): Parsley[A] = crushLevels(table)

    private def crushLevels[A](lvls: Prec[A]): Parsley[A] = lvls match {
        case Atoms(atom0, atoms @ _*) => choice((atom0 +: atoms): _*)
        case Level(lvls, ops) => ops.chain(crushLevels(lvls))
    }

}
