package github.fatalcatharsis

import jakarta.persistence.*
import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy

@Entity
@Table(name = "TEST")
class Test {
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @ManyToOne(cascade = [CascadeType.PERSIST, CascadeType.MERGE], optional = false, fetch = FetchType.EAGER, targetEntity = Reference::class)
    @JoinColumn(name = "REFERENCE_ID")
    var ref : Reference? = null
}

@Entity
@Table(name = "TEST_LAZY")
class TestLazy {
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id : Long? = null

    @ManyToOne(cascade = [CascadeType.PERSIST, CascadeType.MERGE], optional = false, fetch = FetchType.LAZY, targetEntity = Reference::class)
    @JoinColumn(name = "REFERENCE_ID")
    var ref : Reference? = null
}

@Entity
@Table(name = "REFERENCE")
@Inheritance(strategy = InheritanceType.JOINED)
class Reference {
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id : Long? = null

    @Column(name = "NAME")
    var name : String? = null
}

@Entity
@Table(name = "EXTENDED_REFERENCE")
class ExtendedReference : Reference() {
    @Column(name = "NUM")
    var num : Long? = null
}

fun main() {
    val properties = mutableMapOf(
        "javax.persistence.jdbc.url" to "jdbc:postgresql://localhost:5432/postgres",
        "javax.persistence.schema-generation.database.action" to "drop-and-create",
        "javax.persistence.jdbc.user" to "postgres",
        "javax.persistence.jdbc.password" to "password",
        "javax.persistence.jdbc.driver" to "org.postgresql.Driver"
    )

    val entityManagerFactory = Persistence.createEntityManagerFactory("wyatt-test", properties)

    entityManagerFactory.createEntityManager().transaction {
        val newRef = ExtendedReference().apply {
            name = "asdf"
            num = 6
        }
        val entity = Test().apply {
            ref = newRef
        }
        persist(entity)

        val lazyEntity = TestLazy().apply {
            ref = newRef
        }
        persist(lazyEntity)

        close()
    }

    entityManagerFactory.createEntityManager().transaction {
        // fetch object that creates account as proxy
        val result = find(TestLazy::class.java, 1)
        assert(result.ref is HibernateProxy)
        // reference object on eager relationship is the proxy object and is NOT and instance of it's join type
        val notLazy = find(Test::class.java, 1)
        assert(notLazy.ref is HibernateProxy)
        assert(notLazy.ref !is ExtendedReference)
        // however, if you unproxy it, it is
        assert(Hibernate.unproxy(notLazy.ref) is ExtendedReference)

        println("assertions are true!")

        close()
    }
}

fun <T> EntityManager.transaction(block : EntityManager.() -> T) : T {
    this.transaction.begin()
    val result = this.block()
    this.transaction.commit()
    return result
}