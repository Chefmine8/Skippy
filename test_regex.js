const regex = /(eyJhbGciOiJIUzI1Ni[\w-]+\.[\w-]+\.[\w-]+)/;
const text1 = '{"token":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"}';
const m1 = text1.match(regex);
console.log("Match 1:", m1 ? m1[1] : "null");
