둘러보기
========

그래프QL은 크게 두 부분으로 구성되어 있습니다.

* 질의와 반환 데이터 유형을 정의하는 서버측의 *스키마*

* 실행할 쿼리와 구하려는 데이터를 특정하기 위한 클라이언트 질의어

클라이언트 질의어의 형식과 그에 대해 서버가 수행해야 하는 일을 자세히 확인하려면 `그래프QL 명세 <https://facebook.github.io/graphql>`_ 를 참고하세요.

이 라이브러리, '라시니아(Lacinia)'는 서버측 핵심 기능을 클로저 문화에 맞춰 구현한 것입니다.


스키마
------

그래프QL 명세에는 서버측 스키마 정의어가 있습니다. 예를 들어, 객체 유형을 정의할 때는 ``type`` 키워드를 사용하게 정의되어 있습니다.

라시니아에서는 서버측 스키마를 클로저 데이터(키와 값의 맵)로 정의합니다. 다음 예와 같이, 정의할 수 있는 데이터의 종류를 맵의 최상위 키로 확인할 수 있습니다.

.. literalinclude:: ../dev-resources/star-wars-schema.edn
   :language: clojure

예에서는 *human(인간)*과 *droid(로봇)* 객체(objects)를 정의했군요. 이 둘은 많은 점을 공유하고 있어, 이를 나타내기 위해 *character(캐릭터)* 인터페이스(interfaces)를 정의했습니다.

이 데이터에 접근하려면 어떻게 할까요? 스키마 예에서 정의한 다음 세 질의(queries)를 이용하면 됩니다.

* hero

* human

* droid

이 예에서는 각 질의가 매치된 객체 하나를 반환합니다.
하지만 질의 하나가 여러 개의 객체를 담은 리스트를 반환하는 경우도 많습니다.

데이터는 어디서 가져오나요
--------------------------

스키마는 질의로 받을 데이터의 *모양*만을 정의합니다. 데이터가 어디서 오는지는 스키마로는 알 수 없죠.
객체-관계 매핑(ORM) 계층이었다면 데이터베이스의 표와 열을 같이 정의하라고 하겠지만, 그래프QL(그리고 라시니아)에서는 데이터가 어디서 오는지를 굳이 알려고 하지 않습니다.

데이터를 가져오는 일은 :doc:`필드 리졸브 함수 <resolve/index>`가 담당합니다.
EDN 파일은 데이터일 뿐입니다. EDN 데이터에는 함수를 가리키는 키워드만 넣어둡니다. EDN 데이터를 메모리에 올린 뒤, 키워드를 보고 실제 함수와 연결합니다.

스키마 컴파일
-------------

스키마는 처음에는 단순한 데이터 구조일 뿐입니다. 여기에 필드 리졸버를 붙이고 *컴파일*
합니다.

.. literalinclude:: ../dev-resources/org/example/schema.clj
    :language: clojure

``attach-resolvers`` 함수는 스키마 트리를 탐색하며 ``:resolve`` 키의 값들을 실제 함수로 교체합니다.
함수들을 올바른 위치에 놓은 뒤, 스키마를 컴파일하여 실행할 수 있도록 합니다.

컴파일 과정에서는 스키마 오류 검사, 기본값 적용, 클라이언트용 스키마 정보 준비, 그 외 몇 가지 처리를 수행합니다.
``compile``이 처리해야 하는 데이터 구조는 상당히 복잡합니다. 따라서 :doc:`clojure.spec <spec>`을 이용하여 유효성을 검사합니다.

그래프QL 인터페이스 정의어(IDL) 불러오기
----------------------------------------

라시니아는 `그래프QL 인터페이스 정의어 <https://github.com/facebook/graphql/pull/90>`_ 로 쓰인 스키마를 해석하여 클로저 데이터 구조로 변환하는 기능도 제공합니다.

자세한 점은 :doc:`그래프QL 인터페이스 정의어 해석 <schema/parsing>` 을 참고하세요.

질의 실행
---------

스키마를 준비했다면, 이제 질의를 실행할 수 있습니다.

.. literalinclude:: _examples/overview-exec-query.edn
   :language: clojure

.. sidebar:: 순서 유지 맵?

   `#ordered/map``이 사용된 것에서 알 수 있듯이, 반환되는 맵의 필드들은 질의서에 특정한 것과 `동일한 순서` [#order]_ 로 반환됩니다.
   문서를 간결하게 유지하기 위해 이 문서에서는 대부분 순서 유지 맵 대신 기본 맵으로 표기할 것입니다.

The query string is parsed and matched against the queries defined in the schema.

The two nils are variables to be used executing the query, and an application context.

In GraphQL, queries can pass arguments (such as ``id``) and queries identify
exactly which fields
of the matching objects are to be returned.
This query can be stated as `just provide the name of the human with id '1001'`.

This is a successful query, it returns a result map [#result]_ with a ``:data`` key.
A failed query would return a map with an ``:errors`` key.
A query can even be partially successful, returning as much data as it can, but also errors.

Inside ``:data`` is a key corresponding to the query, ``:human``, whose value is the single
matching human.  Other queries might return a list of matches.
Since we requested just a slice of a full human object, just the human's name, the map has just a single
``:name`` key.

.. [#order] This shouldn't be strictly necessary (JSON and EDN don't normally care about key order, and
   keys can appear in arbitrary order),
   but having consistent ordering makes writing tests involving GraphQL queries easier: you can
   typically check the textual, not parsed, version of the result map directly against an expected string value.

.. [#result] In GraphQL's specification, this is referred to as the "response"; in practice,
   this result data forms the body of a response map (when using Ring or Pedestal). Lacinia
   uses the terms `result map` or `result data` to keep these ideas distinct.
