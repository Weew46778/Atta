/*
 * Minimal ZIP writer (store-only) so we can build a .mcpack (a ZIP renamed to .mcpack)
 * entirely in the browser without external dependencies.
 */

const Zip = (() => {
  const CRC_TABLE = (() => {
    const t = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
      t[n] = c >>> 0;
    }
    return t;
  })();

  function crc32(bytes) {
    let c = 0xFFFFFFFF;
    for (let i = 0; i < bytes.length; i++) {
      c = CRC_TABLE[(c ^ bytes[i]) & 0xFF] ^ (c >>> 8);
    }
    return (c ^ 0xFFFFFFFF) >>> 0;
  }

  function u16(v) { return [v & 0xFF, (v >>> 8) & 0xFF]; }
  function u32(v) { return [v & 0xFF, (v >>> 8) & 0xFF, (v >>> 16) & 0xFF, (v >>> 24) & 0xFF]; }

  function strBytes(s) {
    if (typeof s === 'string') {
      return new TextEncoder().encode(s);
    }
    return new Uint8Array(s);
  }

  // files: [{ path: 'dir/file.png', data: Uint8Array|string|Blob }]
  function buildZip(files) {
    const chunks = [];
    const central = [];
    let offset = 0;
    const dosTime = [0x21, 0x0E]; // arbitrary
    const dosDate = [0x21, 0xA9];

    for (const f of files) {
      const name = strBytes(f.path);
      const data = strBytes(f.data);
      const crc = crc32(data);
      const nameLen = name.length;

      const lfh = [
        0x50, 0x4B, 0x03, 0x04,   // local header
        ...u16(20),               // version needed
        ...u16(0x0800),           // flags: UTF-8 names
        ...u16(0),                // method 0 (store)
        ...dosTime, ...dosDate,
        ...u32(crc),
        ...u32(data.length),      // compressed
        ...u32(data.length),      // uncompressed
        ...u16(nameLen),
        ...u16(0),                // extra len
        ...name,
        ...data,
      ];
      chunks.push(new Uint8Array(lfh));
      central.push({ name, crc, size: data.length, offset, nameLen });
      offset += lfh.length;
    }

    const cdStart = offset;
    let cd = [];
    for (const c of central) {
      const rec = [
        0x50, 0x4B, 0x01, 0x02,   // central dir sig
        ...u16(20),               // version made by
        ...u16(20),               // version needed
        ...u16(0x0800),
        ...u16(0),                // method
        ...dosTime, ...dosDate,
        ...u32(c.crc),
        ...u32(c.size),
        ...u32(c.size),
        ...u16(c.nameLen),
        ...u16(0),                // extra
        ...u16(0),                // comment
        ...u16(0),                // disk start
        ...u16(0),                // internal attrs
        ...u32(0),                // external attrs
        ...u32(c.offset),
        ...c.name,
      ];
      cd = cd.concat(rec);
    }
    const cdLen = cd.length;
    const eocd = [
      0x50, 0x4B, 0x05, 0x06,     // end of central dir
      ...u16(0), ...u16(0),
      ...u16(central.length),
      ...u16(central.length),
      ...u32(cdLen),
      ...u32(cdStart),
      ...u16(0),
    ];
    chunks.push(new Uint8Array(cd));
    chunks.push(new Uint8Array(eocd));

    return new Blob(chunks, { type: 'application/zip' });
  }

  function canvasToBytes(canvas) {
    return new Promise((resolve, reject) => {
      canvas.toBlob((blob) => {
        if (!blob) return reject(new Error('toBlob failed'));
        resolve(blob.arrayBuffer().then((buf) => new Uint8Array(buf)));
      }, 'image/png');
    });
  }

  function download(blob, filename) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(url), 4000);
  }

  return { buildZip, canvasToBytes, download, crc32 };
})();

window.Zip = Zip;
