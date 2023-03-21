package github.fatalcatharsis

import jakarta.persistence.*
import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy

interface HasNameReference {
    val nameRef : HasName?
}

interface HasName {
    val name : String?
}

@Entity
@Table(name = "TEST")
class TestEager : HasNameReference {
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @ManyToOne(cascade = [CascadeType.PERSIST, CascadeType.MERGE], optional = false, fetch = FetchType.EAGER, targetEntity = Reference::class)
    @JoinColumn(name = "REFERENCE_ID")
    var ref : Reference? = null

    override val nameRef: HasName? get() = ref
}

@Entity
@Table(name = "TEST_LAZY")
class TestLazy : HasNameReference {
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id : Long? = null

    @ManyToOne(cascade = [CascadeType.PERSIST, CascadeType.MERGE], optional = false, fetch = FetchType.LAZY, targetEntity = Reference::class)
    @JoinColumn(name = "REFERENCE_ID")
    var ref : Reference? = null

    override val nameRef: HasName? get() = ref
}

@Entity
@Table(name = "REFERENCE")
@Inheritance(strategy = InheritanceType.JOINED)
class Reference : HasName {
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id : Long? = null

    @Column(name = "NAME")
    override var name : String? = null
}

@Entity
@Table(name = "EXTENDED_REFERENCE")
class ExtendedReference : Reference() {
    @Column(name = "NUM")
    var num : Long? = null
}

@Entity
@Table(name = "TEST_NON_POLY_EAGER")
class TestNonPolyEager : HasNameReference {
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id : Long? = null

    @ManyToOne(cascade = [CascadeType.PERSIST, CascadeType.MERGE], optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "REFERENCE_ID")
    var ref : NonPolyReference? = null

    override val nameRef: HasName? get() = ref
}

@Entity
@Table(name = "TEST_NON_POLY_LAZY")
class TestNonPolyLazy : HasNameReference {
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id : Long? = null

    @ManyToOne(cascade = [CascadeType.PERSIST, CascadeType.MERGE], optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "REFERENCE_ID")
    var ref : NonPolyReference? = null

    override val nameRef: HasName? get() = ref
}

@Entity
@Table(name = "NON_POLY_REFERENCE")
class NonPolyReference : HasName {
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id : Long? = null

    @Column(name = "NAME")
    override var name : String? = null
}

fun main() {
    val properties = mutableMapOf(
        "javax.persistence.jdbc.url" to "jdbc:postgresql://localhost:5432/postgres",
        "javax.persistence.schema-generation.database.action" to "drop-and-create",
        "javax.persistence.jdbc.user" to "postgres",
        "javax.persistence.jdbc.password" to "password",
        "javax.persistence.jdbc.driver" to "org.postgresql.Driver",
        "hibernate.show_sql" to "true"
    )

    val entityManagerFactory = Persistence.createEntityManagerFactory("wyatt-test", properties)

    entityManagerFactory.createEntityManager().transaction {
        val newRef = ExtendedReference().apply {
            name = "asdf"
            num = 6
        }
        val entity = TestEager().apply {
            ref = newRef
        }
        persist(entity)

        val lazyEntity = TestLazy().apply {
            ref = newRef
        }
        persist(lazyEntity)

        val newNonPolyReference = NonPolyReference().apply {
            name = "asdf"
        }
        val nonPolyEntity = TestNonPolyEager().apply {
            ref = newNonPolyReference
        }
        persist(nonPolyEntity)

        val nonPolyLazy = TestNonPolyLazy().apply {
            ref = newNonPolyReference
        }
        persist(nonPolyLazy)

        close()
    }

    // TEST of various many to one scenarios
    runTest<TestNonPolyEager>("TEST 1", "fetch entity with eager many to one non polymorphic reference", entityManagerFactory)
    runTest<TestNonPolyLazy>("TEST 2","fetch entity with lazy many to one non polymorphic reference", entityManagerFactory)
    runTest<TestEager>("TEST 3","fetch entity with eager many to one with polymorphic reference", entityManagerFactory)
    runTest<TestLazy>("TEST 4","fetch entity with lazy many to one with polymorphic reference", entityManagerFactory)

    // Showcase issue occurring
    println("TEST 5 - showcase of behavior fetching entity with lazy ref first in session")
    entityManagerFactory.createEntityManager().transaction {
        println("fetch object with lazy ref that creates ref as proxy")
        val result = find(TestLazy::class.java, 1)
        assertMessage(result.ref is HibernateProxy, "ref of initial lazy fetch is proxy")

        println("now fetch object with eager ref that reference the same ref fetched previously")
        val notLazy = find(TestEager::class.java, 1)
        assertMessage(notLazy.ref is HibernateProxy, "ref of subsequent eager fetch after first fetch is a proxy")
        assertMessage(notLazy.ref !is ExtendedReference, "the generated proxy is not of the correct concrete type")

        println("SQL should occur here when I attempt to unproxy")
        val unproxiedEntity = Hibernate.unproxy(notLazy.ref)
        assertMessage(unproxiedEntity is ExtendedReference, "after unproxying the object, it is of the correct concrete type")
        close()
    }
    println("TEST 5 - completed")
}

inline fun <reified T : HasNameReference> runTest(testName : String, testDesription : String, entityManagerFactory : EntityManagerFactory) {
    println("$testName - $testDesription")
    entityManagerFactory.createEntityManager().transaction {
        println("find entity")
        val result = find(T::class.java, 1)
        println("getting reference - usually no sql")
        val ref = result.nameRef
        assertMessage(ref is HibernateProxy, "Reference is a proxy")
        println("getting name value from ref object - will spawn sql for lazy")
        println(ref!!.name)
        close()
    }
    println("$testName - completed\n")
}

fun assertMessage(assertion : Boolean, description : String) {
    println("$description - $assertion")
}

fun <T> EntityManager.transaction(block : EntityManager.() -> T) : T {
    this.transaction.begin()
    val result = this.block()
    this.transaction.commit()
    return result
}